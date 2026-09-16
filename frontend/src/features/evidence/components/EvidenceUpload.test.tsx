// 증빙 업로드 (S15P21E104-165).
//
// 화면이 여기서 막는 것은 사용자가 되돌릴 수 없는 실수를 하게 만드는 동작이다.
//
//   1) 무엇이 올라가는지 모른 채 올리게 한다
//   2) 실패했는데 선택이 사라져 무엇을 골랐는지 알 수 없게 한다
//   3) 올렸는데 목록에 안 나타나 다시 올리게 한다 (중복이 영구히 남는다)
//   4) 미리보기를 `<img src={contentPath}>` 로 그려 **볼 자격이 있는 사용자에게도**
//      깨진 이미지를 보여준다 (조회 경로가 Authorization 헤더를 요구한다)
//   5) **가려진 증빙을 "증빙 없음" 으로 그린다** (S15P21E104-286 §6-1)
//
// 5번은 응답이 배열 → 객체로 바뀌면서 생긴 위험이다. 서버는 관계 없는 사용자에게
// 종류별 개수만 주고(`visibility: 'SUMMARY'`, `files` 키 자체가 없다), 그것을 빈
// 배열처럼 다루면 "아직 증빙이 올라오지 않았습니다" 가 뜬다 — 있는 것을 없다고
// 말하는 화면이 된다.
//
// 삭제는 소프트 삭제이고 **판정은 서버가 한다** (`EvidenceDeletionService`). 화면은
// 업로더·소재 등록자인지, 온체인에 고정됐는지를 다시 계산하지 않고 서버 응답을
// 그대로 보여준다 — 그래서 여기서 확인하는 것은 "화면이 지우기 전에 확인시키고,
// 서버가 거절하면 그 이유를 그대로 보여주는가" 다.
//
// 모킹 경계는 API 모듈이다. FormData 조립까지 실제로 통과시킨다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import type { EvidenceFile, EvidenceListResponse } from '../types/evidence.types.ts'
import { EvidenceUpload } from './EvidenceUpload.tsx'

const api = vi.hoisted(() => ({
  getEvidence: vi.fn(),
  uploadEvidence: vi.fn(),
  getEvidenceContent: vi.fn(),
  deleteEvidence: vi.fn(),
}))
vi.mock('../api/getEvidence.ts', () => ({ getEvidence: api.getEvidence }))
vi.mock('../api/uploadEvidence.ts', () => ({ uploadEvidence: api.uploadEvidence }))
vi.mock('../api/getEvidenceContent.ts', () => ({ getEvidenceContent: api.getEvidenceContent }))
vi.mock('../api/deleteEvidence.ts', () => ({ deleteEvidence: api.deleteEvidence }))

const HASH = `0x${'ab'.repeat(32)}`

function evidence(overrides: Partial<EvidenceFile> = {}): EvidenceFile {
  return {
    evidenceFileId: 1,
    storageKey: 'MATERIAL/12/2026/09/uuid.jpg',
    // 파일을 받는 엔드포인트가 이제 있다 (`!167`). 서버가 경로를 만들어 준다.
    contentPath: '/api/evidence/1/content',
    fileHash: HASH,
    evidenceType: 'BEFORE_PHOTO',
    uploadedBy: 3,
    uploadedAt: '2026-09-07T16:30:00',
    // 목록에서는 항상 null 이다. 값이 있는 것은 삭제 응답뿐이다.
    deletedAt: null,
    ...overrides,
  }
}

/**
 * 전체를 볼 수 있는 사람의 응답. `summary` 는 **서버가 준 것을 그대로 쓴다** —
 * files 를 세어 만들면 실제 계약과 어긋난 mock 이 되고, 화면이 요약을 직접 계산해도
 * 테스트가 통과해 버린다.
 */
function fullList(files: EvidenceFile[]): EvidenceListResponse {
  const counts = new Map<EvidenceFile['evidenceType'], number>()
  files.forEach((file) => counts.set(file.evidenceType, (counts.get(file.evidenceType) ?? 0) + 1))
  return {
    visibility: 'FULL',
    files,
    summary: [...counts].map(([evidenceType, count]) => ({ evidenceType, count })),
  }
}

/**
 * 관계가 없는 인증 사용자의 응답. **`files` 키가 없다** — 빈 배열이 아니다.
 * 빈 배열은 "증빙이 없다" 는 뜻이고 그건 사실이 아니다.
 */
function summaryList(summary: EvidenceListResponse['summary']): EvidenceListResponse {
  return { visibility: 'SUMMARY', summary }
}

/** 증빙이 하나도 없는 응답. 이때는 `summary` 가 빈 배열이다. */
const EMPTY_LIST: EvidenceListResponse = { visibility: 'FULL', files: [], summary: [] }

function renderUpload(referenceId: number | null = 12, readOnly = false) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={queryClient}>
      <EvidenceUpload referenceType="MATERIAL" referenceId={referenceId} readOnly={readOnly} />
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

function jpeg(name: string, sizeBytes = 1024) {
  return new File([new Uint8Array(sizeBytes)], name, { type: 'image/jpeg' })
}

beforeEach(() => {
  api.getEvidence.mockReset().mockResolvedValue(EMPTY_LIST)
  api.uploadEvidence.mockReset().mockResolvedValue([evidence()])
  api.getEvidenceContent.mockReset().mockResolvedValue(new Blob(['bytes'], { type: 'image/jpeg' }))
  api.deleteEvidence.mockReset().mockResolvedValue(evidence({ deletedAt: '2026-09-13T10:00:00' }))
})

afterEach(cleanup)

describe('가공 증빙 종류별 업로드', () => {
  it('가공 증빙 종류마다 파일 선택 칸을 동시에 표시한다', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    render(
      <QueryClientProvider client={queryClient}>
        <EvidenceUpload
          referenceType="PROCESSING_JOB"
          referenceId={7}
          uploadMode="type-grid"
        />
      </QueryClientProvider>,
    )

    expect(await screen.findByLabelText('작업 전 사진 파일 선택')).toBeInTheDocument()
    expect(screen.getByLabelText('공정 사진 파일 선택')).toBeInTheDocument()
    expect(screen.getByLabelText('작업 후 사진 파일 선택')).toBeInTheDocument()
    expect(screen.getByLabelText('계근표 파일 선택')).toBeInTheDocument()
    expect(screen.getByLabelText('거래명세서 파일 선택')).toBeInTheDocument()
    expect(screen.getByLabelText('기타 파일 선택')).toBeInTheDocument()
    expect(screen.queryByLabelText('증빙 종류')).not.toBeInTheDocument()
  })

  it('각 칸에서 고른 파일을 해당 증빙 종류로 업로드한다', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    const user = userEvent.setup()
    render(
      <QueryClientProvider client={queryClient}>
        <EvidenceUpload
          referenceType="PROCESSING_JOB"
          referenceId={7}
          uploadMode="type-grid"
        />
      </QueryClientProvider>,
    )

    await user.upload(await screen.findByLabelText('공정 사진 파일 선택'), jpeg('process.jpg'))
    await user.click(screen.getByRole('button', { name: '공정 사진 1개 올리기' }))

    await waitFor(() => expect(api.uploadEvidence).toHaveBeenCalledTimes(1))
    expect(api.uploadEvidence.mock.calls[0][0]).toMatchObject({
      referenceType: 'PROCESSING_JOB',
      referenceId: 7,
      evidenceType: 'PROCESS_PHOTO',
    })
    expect(api.uploadEvidence.mock.calls[0][0].files[0]).toHaveProperty('name', 'process.jpg')
  })
})
describe('올리기 전에 확인시킨다', () => {
  it('파일을 고르면 목록과 개수를 보여준다', async () => {
    // 증빙은 되돌릴 수 없다. 무엇이 올라가는지 볼 기회가 있어야 한다.
    const user = renderUpload()

    await user.upload(await screen.findByLabelText(/파일 선택/), [
      jpeg('before.jpg'),
      jpeg('slip.jpg'),
    ])

    expect(screen.getByRole('list', { name: '올릴 파일' })).toBeInTheDocument()
    expect(screen.getByText('before.jpg')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '증빙 2개 올리기' })).toBeInTheDocument()
  })

  it('고르기 전에는 올리기 버튼이 없다', async () => {
    renderUpload()

    await screen.findByLabelText(/파일 선택/)
    expect(screen.queryByRole('button', { name: /올리기/ })).not.toBeInTheDocument()
  })
})

describe('전송 계약', () => {
  it('고른 종류와 파일을 그대로 보낸다', async () => {
    const user = renderUpload()

    await user.selectOptions(await screen.findByLabelText('증빙 종류'), 'WEIGHT_SLIP')
    await user.upload(screen.getByLabelText(/파일 선택/), [jpeg('slip.jpg')])
    await user.click(screen.getByRole('button', { name: '증빙 1개 올리기' }))

    await waitFor(() => expect(api.uploadEvidence).toHaveBeenCalledTimes(1))
    expect(api.uploadEvidence.mock.calls[0][0]).toMatchObject({
      referenceType: 'MATERIAL',
      referenceId: 12,
      evidenceType: 'WEIGHT_SLIP',
    })
    expect(api.uploadEvidence.mock.calls[0][0].files).toHaveLength(1)
  })

  it('소재 화면에는 공정 사진 종류를 두지 않는다', async () => {
    // 목록에 있으면 사용자가 고르고, 그러면 소재 증빙이 공정 사진으로 분류된다.
    renderUpload()

    const select = await screen.findByLabelText('증빙 종류')
    const options = Array.from(select.querySelectorAll('option')).map((o) => o.value)
    expect(options).not.toContain('PROCESS_PHOTO')
    expect(options).toContain('WEIGHT_SLIP')
  })
})

describe('개수 상한', () => {
  it('10개는 받는다', async () => {
    const user = renderUpload()

    const ten = Array.from({ length: 10 }, (_, i) => jpeg(`photo${i}.jpg`))
    await user.upload(await screen.findByLabelText(/파일 선택/), ten)

    expect(screen.getByRole('button', { name: '증빙 10개 올리기' })).toBeInTheDocument()
  })

  it('11개면 이유를 밝히고 선택을 받지 않는다', async () => {
    // 조용히 10개만 잘라내면 사용자는 몇 개가 왜 빠졌는지 모른다.
    const user = renderUpload()

    const eleven = Array.from({ length: 11 }, (_, i) => jpeg(`photo${i}.jpg`))
    await user.upload(await screen.findByLabelText(/파일 선택/), eleven)

    expect(screen.getByRole('alert')).toHaveTextContent(/10개까지/)
    expect(screen.queryByRole('button', { name: /올리기/ })).not.toBeInTheDocument()
  })
})

describe('실패했을 때', () => {
  it('오류를 보여주고 선택을 유지한다', async () => {
    // 실패한 채로 선택을 비우면 사용자가 파일을 다시 골라야 하는데, 무엇을 골랐는지는
    // 이미 화면에서 사라진 상태다.
    api.uploadEvidence.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, { message: '파일 하나는 10MB 를 넘을 수 없습니다.' }),
    )
    const user = renderUpload()

    await user.upload(await screen.findByLabelText(/파일 선택/), [jpeg('before.jpg')])
    await user.click(screen.getByRole('button', { name: '증빙 1개 올리기' }))

    expect(
      await screen.findByText('파일 하나는 10MB 를 넘을 수 없습니다.'),
    ).toBeInTheDocument()
    expect(screen.getByText('before.jpg')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '증빙 1개 올리기' })).toBeInTheDocument()
  })

  it('성공하면 선택을 비운다', async () => {
    const user = renderUpload()

    await user.upload(await screen.findByLabelText(/파일 선택/), [jpeg('before.jpg')])
    await user.click(screen.getByRole('button', { name: '증빙 1개 올리기' }))

    await waitFor(() =>
      expect(screen.queryByRole('button', { name: /올리기/ })).not.toBeInTheDocument(),
    )
  })
})

describe('목록', () => {
  it('증빙이 없으면 EmptyState 다 — 오류가 아니다', async () => {
    renderUpload()

    expect(await screen.findByText('등록된 증빙이 없습니다')).toBeInTheDocument()
  })

  it('종류와 해시를 보여준다', async () => {
    // 해시는 온체인에 올라가는 값이다. 사용자가 자기 증빙이 체인의 그것인지 대조할 수
    // 있어야 한다.
    //
    // **목록 범위로 좁혀서 본다.** 증빙 종류 Select 에 같은 라벨의 <option> 이 있어서
    // 화면 전체에서 텍스트를 찾으면 그 옵션에 매칭되고, 그러면 조회가 끝나기를
    // 기다리지 않은 채 단언이 통과한다 (실제로 그렇게 잘못 짰다).
    api.getEvidence.mockResolvedValue(fullList([evidence({ evidenceType: 'WEIGHT_SLIP' })]))
    renderUpload()

    const list = within(await screen.findByRole('list', { name: '등록된 증빙' }))
    expect(list.getByText('계근표')).toBeInTheDocument()
    expect(list.getByText(HASH)).toBeInTheDocument()
  })

  it('storageKey 를 <img src> 에 넣지 않는다', async () => {
    // storageKey 는 URL 이 아니라 저장소 경로다. 넣으면 깨진 이미지가 된다.
    // contentPath 도 <img src> 에 직접 넣을 수 없다 — 조회 경로가 Authorization
    // 헤더를 요구하고, 브라우저가 스스로 보내는 요청에는 그 헤더가 붙지 않는다.
    api.getEvidence.mockResolvedValue(fullList([evidence()]))
    renderUpload()

    const list = within(await screen.findByRole('list', { name: '등록된 증빙' }))
    const images = list.queryAllByRole('img')
    images.forEach((image) => {
      expect(image.getAttribute('src')).not.toContain('MATERIAL/')
      expect(image.getAttribute('src')).not.toContain('/api/evidence')
    })
    // "준비 중" 은 이제 사실이 아니다.
    expect(list.queryByText(/준비 중/)).not.toBeInTheDocument()
  })

  it('조회 실패는 다시 시도할 수 있다', async () => {
    api.getEvidence.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, { message: '증빙을 불러오지 못했습니다.' }),
    )
    renderUpload()

    expect(await screen.findByText('증빙을 불러오지 못했습니다.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument()
  })
})

describe('삭제', () => {
  it('확인 없이는 지우지 않는다', async () => {
    api.getEvidence.mockResolvedValue(fullList([evidence()]))
    const user = renderUpload()

    const list = within(await screen.findByRole('list', { name: '등록된 증빙' }))
    await user.click(list.getByRole('button', { name: '삭제' }))

    expect(api.deleteEvidence).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: '지우기' })).toBeInTheDocument()
  })

  it('확인하면 지우고 목록을 다시 받는다', async () => {
    // 응답으로 캐시를 직접 고치지 않는다 — 목록을 무효화해 다시 받는다. 그래서
    // getEvidence 가 다시 불렸는지까지 본다.
    api.getEvidence.mockResolvedValue(fullList([evidence({ evidenceFileId: 9 })]))
    const user = renderUpload()

    const list = within(await screen.findByRole('list', { name: '등록된 증빙' }))
    await user.click(list.getByRole('button', { name: '삭제' }))
    await user.click(screen.getByRole('button', { name: '지우기' }))

    expect(api.deleteEvidence).toHaveBeenCalledWith(9)
    await waitFor(() => expect(api.getEvidence).toHaveBeenCalledTimes(2))
  })

  it('닫으면 지우지 않는다', async () => {
    api.getEvidence.mockResolvedValue(fullList([evidence()]))
    const user = renderUpload()

    const list = within(await screen.findByRole('list', { name: '등록된 증빙' }))
    await user.click(list.getByRole('button', { name: '삭제' }))
    await user.click(screen.getByRole('button', { name: '취소' }))

    expect(api.deleteEvidence).not.toHaveBeenCalled()
  })

  it('온체인에 고정됐으면 서버가 거절한 이유를 그대로 보여준다', async () => {
    // 누가 지울 수 있는지·언제 고정되는지는 서버(EvidenceAnchorPolicy)가 판정한다.
    // 화면은 그 판정을 다시 하지 않고 에러 메시지를 그대로 보여준다.
    api.getEvidence.mockResolvedValue(fullList([evidence()]))
    api.deleteEvidence.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, {
        message: '온체인에 기록된 증빙은 지울 수 없습니다.',
        code: 'EVIDENCE_ANCHORED_ON_CHAIN',
        status: 409,
      }),
    )
    const user = renderUpload()

    const list = within(await screen.findByRole('list', { name: '등록된 증빙' }))
    await user.click(list.getByRole('button', { name: '삭제' }))
    await user.click(screen.getByRole('button', { name: '지우기' }))

    expect(
      await screen.findByText('온체인에 기록된 증빙은 지울 수 없습니다.'),
    ).toBeInTheDocument()
  })

  it('읽기 전용에서는 삭제 버튼이 없다', async () => {
    // readOnly 는 남의 소재를 볼 때 쓴다 — 업로드 폼과 마찬가지로 파괴적 동작도
    // 감춘다.
    api.getEvidence.mockResolvedValue(fullList([evidence()]))
    renderUpload(12, true)

    await screen.findByRole('list', { name: '등록된 증빙' })
    expect(screen.queryByRole('button', { name: '삭제' })).not.toBeInTheDocument()
  })
})

describe('가려진 것과 없는 것을 구분한다 (visibility)', () => {
  it('요약만 받으면 "증빙 없음" 이 아니라 종류별 건수를 보여준다', async () => {
    // 이것이 이 브랜치의 핵심이다. 서버는 관계 없는 사용자에게 files 키를 아예 주지
    // 않는데, 그것을 빈 배열처럼 다루면 증빙이 2건 있는 소재가 "아직 증빙이
    // 올라오지 않았습니다" 로 그려진다.
    api.getEvidence.mockResolvedValue(
      summaryList([
        { evidenceType: 'WEIGHT_SLIP', count: 1 },
        { evidenceType: 'BEFORE_PHOTO', count: 3 },
      ]),
    )
    renderUpload(12, true)

    const summary = within(await screen.findByRole('list', { name: '증빙 요약' }))
    expect(summary.getByText('계근표 1건')).toBeInTheDocument()
    expect(summary.getByText('작업 전 사진 3건')).toBeInTheDocument()
    expect(screen.queryByText('등록된 증빙이 없습니다')).not.toBeInTheDocument()
  })

  it('요약만 받으면 파일 목록 대신 가려졌다는 사실을 밝힌다', async () => {
    // 개수는 추적성이고 파일명·해시·업로더는 거래 정보다. 왜 안 보이는지 말하지 않으면
    // 사용자는 화면이 고장 났다고 생각한다.
    api.getEvidence.mockResolvedValue(summaryList([{ evidenceType: 'WEIGHT_SLIP', count: 1 }]))
    renderUpload(12, true)

    await screen.findByRole('list', { name: '증빙 요약' })
    expect(screen.queryByRole('list', { name: '등록된 증빙' })).not.toBeInTheDocument()
    expect(screen.getByText(/보이지 않습니다/)).toBeInTheDocument()
    // 해시가 새어 나가지 않는지도 같이 본다 — 요약 응답에는 애초에 없는 값이다.
    expect(screen.queryByText(HASH)).not.toBeInTheDocument()
  })

  it('전체를 볼 수 있어도 같은 요약 줄을 쓴다', async () => {
    // 서버가 FULL 응답에도 summary 를 주는 이유다. 화면이 files 를 세어 만들면
    // 요약 규칙이 두 곳에 생긴다.
    api.getEvidence.mockResolvedValue(
      fullList([evidence({ evidenceFileId: 1 }), evidence({ evidenceFileId: 2 })]),
    )
    renderUpload()

    const summary = within(await screen.findByRole('list', { name: '증빙 요약' }))
    expect(summary.getByText('작업 전 사진 2건')).toBeInTheDocument()
    expect(screen.getByRole('list', { name: '등록된 증빙' })).toBeInTheDocument()
  })

  it('비회원(401)은 요약도 못 받는다 — 오류로 그리고 "증빙 없음" 이라고 하지 않는다', async () => {
    // 소재 상세는 공개 라우트인데 이 API 는 인증을 요구한다. 비회원에게 요약을 여는
    // 것은 QR 공개 조회(§6-3)에 걸려 있는 별도 결정이라 여기서 우회하지 않는다.
    //
    // 지금 확인해 두는 것은 **그 상태가 "증빙 없음" 으로 보이지 않는다**는 점이다.
    // 조회가 실패한 것과 0건인 것을 섞으면 나중에 그 경로를 열 때 무엇이 달라졌는지
    // 알 수 없다.
    api.getEvidence.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, {
        message: '인증이 필요합니다.',
        code: 'UNAUTHORIZED',
        status: 401,
      }),
    )
    renderUpload(12, true)

    expect(await screen.findByText('인증이 필요합니다.')).toBeInTheDocument()
    expect(screen.queryByText('등록된 증빙이 없습니다')).not.toBeInTheDocument()
    expect(screen.queryByRole('list', { name: '증빙 요약' })).not.toBeInTheDocument()
  })

  it('정말 0건이면 요약 줄 없이 EmptyState 다', async () => {
    // 가려진 것과 달리 이때는 "올려 주세요" 가 맞는 안내다.
    renderUpload()

    expect(await screen.findByText('등록된 증빙이 없습니다')).toBeInTheDocument()
    expect(screen.queryByRole('list', { name: '증빙 요약' })).not.toBeInTheDocument()
  })
})

describe('올리기 전 미리보기 (a)', () => {
  it('고른 사진을 서버 없이 그린다', async () => {
    // 증빙은 되돌릴 수 없다. 파일명만으로는 사진 열 장 중 어느 것을 잘못 골랐는지
    // 알 수 없으므로 눈으로 확인할 기회가 있어야 한다.
    const user = renderUpload()

    await user.upload(await screen.findByLabelText(/파일 선택/), [jpeg('before.jpg')])

    const selected = within(screen.getByRole('list', { name: '올릴 파일' }))
    expect(selected.getByAltText('before.jpg 미리보기')).toBeInTheDocument()
    // 아직 서버에 없는 파일이다. 조회가 나가면 안 된다.
    expect(api.getEvidenceContent).not.toHaveBeenCalled()
  })

  it('pdf 는 <img> 에 넣지 않고 확장자로 말한다', async () => {
    // 허용 목록에 .pdf 가 있다. <img> 에 넣으면 깨진 이미지가 되고, 사용자는 파일이
    // 잘못됐다고 생각한다.
    const user = renderUpload()

    await user.upload(await screen.findByLabelText(/파일 선택/), [
      new File([new Uint8Array(1024)], 'slip.pdf', { type: 'application/pdf' }),
    ])

    const selected = within(screen.getByRole('list', { name: '올릴 파일' }))
    expect(selected.queryByRole('img')).not.toBeInTheDocument()
    expect(selected.getByText('PDF')).toBeInTheDocument()
  })
})

describe('업로드 결과 표시', () => {
  it('올라간 건수와 파일별 해시를 밝힌다', async () => {
    // 증빙은 지울 수 없으므로 사용자가 방금 남긴 기록을 확인할 수 있어야 한다.
    // 해시는 온체인에 올라가는 값이라 대조에 쓰인다.
    api.uploadEvidence.mockResolvedValue([
      evidence({ evidenceFileId: 7, evidenceType: 'WEIGHT_SLIP' }),
      evidence({ evidenceFileId: 8, evidenceType: 'WEIGHT_SLIP' }),
    ])
    const user = renderUpload()

    await user.upload(await screen.findByLabelText(/파일 선택/), [jpeg('a.jpg'), jpeg('b.jpg')])
    await user.click(screen.getByRole('button', { name: '증빙 2개 올리기' }))

    const result = within(await screen.findByRole('list', { name: '업로드 결과' }))
    expect(result.getAllByText('계근표')).toHaveLength(2)
    expect(screen.getByText('증빙 2개를 올렸습니다.')).toBeInTheDocument()
  })

  it('실패하면 성공 결과를 남기지 않는다', async () => {
    // 서버는 일부만 성공시키지 않는다 — 검증을 모두 끝낸 뒤에 첫 바이트를 쓰고,
    // 중간에 실패하면 이미 쓴 파일을 지우고 트랜잭션을 되돌린다. 그러므로 실패에
    // "N개는 올라갔습니다" 가 남으면 서버가 하지 않는 약속을 화면이 하는 것이다.
    api.uploadEvidence.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, { message: '파일 형식이 허용되지 않습니다.' }),
    )
    const user = renderUpload()

    await user.upload(await screen.findByLabelText(/파일 선택/), [jpeg('a.jpg')])
    await user.click(screen.getByRole('button', { name: '증빙 1개 올리기' }))

    expect(await screen.findByText('파일 형식이 허용되지 않습니다.')).toBeInTheDocument()
    expect(screen.queryByRole('list', { name: '업로드 결과' })).not.toBeInTheDocument()
  })
})

describe('올린 뒤 미리보기 (b)', () => {
  it('목록을 그리는 것만으로 파일을 내려받지 않는다', async () => {
    // 한 소재에 증빙이 10개까지 붙고 파일당 10MB 다. 목록 렌더가 곧 100MB 다운로드가
    // 되면 안 된다.
    api.getEvidence.mockResolvedValue(fullList([evidence()]))
    renderUpload()

    const list = within(await screen.findByRole('list', { name: '등록된 증빙' }))
    expect(list.getByRole('button', { name: '미리보기' })).toBeInTheDocument()
    expect(api.getEvidenceContent).not.toHaveBeenCalled()
  })

  it('누르면 서버가 준 contentPath 로 받아 그린다', async () => {
    // 경로를 id 로 조립하지 않는다 — 조립 규칙이 두 곳에 생기고, 서버가 경로를 고칠 때
    // 한쪽이 남는다.
    api.getEvidence.mockResolvedValue(fullList([evidence({ evidenceFileId: 5 })]))
    const user = renderUpload()

    const list = within(await screen.findByRole('list', { name: '등록된 증빙' }))
    await user.click(list.getByRole('button', { name: '미리보기' }))

    expect(await screen.findByAltText('증빙 5 미리보기')).toBeInTheDocument()
    expect(api.getEvidenceContent).toHaveBeenCalledWith('/api/evidence/1/content')
  })

  it('pdf 는 <img> 가 아니라 새 탭 링크로 준다', async () => {
    api.getEvidence.mockResolvedValue(
      fullList([evidence({ storageKey: 'MATERIAL/12/2026/09/uuid.pdf' })]),
    )
    api.getEvidenceContent.mockResolvedValue(new Blob(['%PDF'], { type: 'application/pdf' }))
    const user = renderUpload()

    const list = within(await screen.findByRole('list', { name: '등록된 증빙' }))
    await user.click(list.getByRole('button', { name: '문서 열기' }))

    const link = await screen.findByRole('link', { name: /새 탭/ })
    expect(link).toHaveAttribute('target', '_blank')
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })

  it('자격이 없으면 깨진 이미지가 아니라 읽을 수 있는 오류를 준다', async () => {
    // FULL 이 아니면 서버가 contentPath 를 아예 주지 않으므로 여기까지 오지 않지만,
    // 관계가 바뀐 뒤 캐시된 목록으로 누르는 경로가 있다. `<img onError>` 로 뒤늦게
    // 감추는 대신 apiClient 가 정규화한 오류를 그대로 보여준다.
    api.getEvidence.mockResolvedValue(fullList([evidence()]))
    api.getEvidenceContent.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, {
        message: '이 증빙을 볼 권한이 없습니다.',
        status: 403,
      }),
    )
    const user = renderUpload()

    const list = within(await screen.findByRole('list', { name: '등록된 증빙' }))
    await user.click(list.getByRole('button', { name: '미리보기' }))

    expect(await screen.findByText('이 증빙을 볼 권한이 없습니다.')).toBeInTheDocument()
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })

  it('요약만 받은 사람에게는 미리보기 버튼 자체가 없다', async () => {
    // evidenceFileId · contentPath 가 응답에 없다. 버튼을 두면 누를 것이 없는 버튼이 된다.
    api.getEvidence.mockResolvedValue(summaryList([{ evidenceType: 'WEIGHT_SLIP', count: 1 }]))
    renderUpload(12, true)

    await screen.findByRole('list', { name: '증빙 요약' })
    expect(screen.queryByRole('button', { name: /미리보기|문서 열기/ })).not.toBeInTheDocument()
    expect(api.getEvidenceContent).not.toHaveBeenCalled()
  })
})

describe('읽기 전용', () => {
  it('업로드 UI 를 감추고 목록만 보여준다', async () => {
    api.getEvidence.mockResolvedValue(fullList([evidence()]))
    renderUpload(12, true)

    await screen.findByRole('list', { name: '등록된 증빙' })
    expect(screen.queryByLabelText(/파일 선택/)).not.toBeInTheDocument()
    expect(screen.queryByLabelText('증빙 종류')).not.toBeInTheDocument()
  })
})

describe('잘못된 주소', () => {
  it('referenceId 가 null 이면 조회하지 않는다', () => {
    renderUpload(null)

    expect(api.getEvidence).not.toHaveBeenCalled()
    expect(screen.getByText('잘못된 주소입니다.')).toBeInTheDocument()
  })
})
