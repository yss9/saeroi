// 공통 파일 업로더 (S15P21E104-253).
//
// 이 컴포넌트가 지켜야 하는 것은 세 가지다.
//
//   1) **정책을 스스로 정하지 않는다.** 허용 형식·크기·개수는 prop 으로만 온다.
//      여기에 박아 두면 도메인마다 다른 정책을 쓸 수 없고, 서버 정책이 바뀔 때
//      한 도메인만 고쳐 놓고 다른 도메인이 조용히 어긋난다.
//   2) **거절을 조용히 하지 않는다.** 버려진 파일의 이름과 이유를 말한다.
//   3) **업로드하지 않는다.** File 객체만 넘긴다.
//
// `accept` 속성은 파일 선택창의 힌트일 뿐 강제가 아니라서 검증을 실제로 해야 한다 —
// 사용자가 "모든 파일" 로 바꾸거나 드래그로 놓으면 그대로 들어온다.

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { FileUploader } from './FileUploader.tsx'
import { validateFiles } from './fileValidation.ts'

afterEach(cleanup)

function file(name: string, type: string, sizeBytes = 1024) {
  return new File([new Uint8Array(sizeBytes)], name, { type })
}

const jpeg = (name = 'photo.jpg', sizeBytes = 1024) => file(name, 'image/jpeg', sizeBytes)

describe('선택만 하고 업로드하지 않는다', () => {
  it('통과한 파일을 그대로 넘긴다', async () => {
    const onChange = vi.fn()
    render(<FileUploader multiple accept=".jpg,.pdf" onChange={onChange} />)
    const user = userEvent.setup()

    await user.upload(screen.getByLabelText('파일 선택'), [jpeg('a.jpg'), file('b.pdf', 'application/pdf')])

    expect(onChange).toHaveBeenCalledTimes(1)
    expect(onChange.mock.calls[0][0].map((f: File) => f.name)).toEqual(['a.jpg', 'b.pdf'])
  })
})

describe('정책은 prop 으로만 온다', () => {
  it('accept 에 없는 형식은 이름을 밝히고 제외한다', () => {
    // 드롭으로 넣는다. `user.upload` 는 accept 속성을 **스스로 존중해서** 맞지 않는
    // 파일을 빼 버리므로, 그 경로로는 우리 검증이 도는지 확인할 수 없다.
    // 실제 브라우저에서 accept 를 우회하는 경로가 바로 드래그 앤 드롭이기도 하다.
    const onChange = vi.fn()
    const { container } = render(<FileUploader multiple accept=".jpg,.png" onChange={onChange} />)

    fireEvent.drop(container.querySelector('.ui-uploader__drop') as HTMLElement, {
      dataTransfer: { files: [jpeg('ok.jpg'), file('doc.pdf', 'application/pdf')] },
    })

    expect(onChange).toHaveBeenCalledWith([expect.objectContaining({ name: 'ok.jpg' })])
    expect(screen.getByRole('alert')).toHaveTextContent('doc.pdf')
  })

  it('accept 를 주지 않으면 형식으로 거르지 않는다', async () => {
    // 기본값으로 특정 형식을 강요하면 다른 도메인이 쓸 수 없다.
    const onChange = vi.fn()
    render(<FileUploader onChange={onChange} />)
    const user = userEvent.setup()

    await user.upload(screen.getByLabelText('파일 선택'), file('weird.bin', 'application/octet-stream'))

    expect(onChange).toHaveBeenCalledWith([expect.objectContaining({ name: 'weird.bin' })])
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('maxFiles 를 주지 않으면 개수를 막지 않는다', async () => {
    // 요청당 10개는 증빙 엔드포인트의 규칙이고, 제품 이미지는 1장이다.
    const onChange = vi.fn()
    render(<FileUploader multiple onChange={onChange} />)
    const user = userEvent.setup()

    const many = Array.from({ length: 30 }, (_, i) => jpeg(`p${i}.jpg`))
    await user.upload(screen.getByLabelText('파일 선택'), many)

    expect(onChange.mock.calls[0][0]).toHaveLength(30)
  })

  it('maxFiles 를 넘기면 일부만 받지 않고 전부 거절한다', async () => {
    // 개수 초과는 요청 단위 실패다. 앞의 몇 개만 받으면 고른 것과 올라간 것이 달라진다.
    const onChange = vi.fn()
    render(<FileUploader multiple maxFiles={2} onChange={onChange} />)
    const user = userEvent.setup()

    await user.upload(screen.getByLabelText('파일 선택'), [jpeg('a.jpg'), jpeg('b.jpg'), jpeg('c.jpg')])

    expect(onChange).toHaveBeenCalledWith([])
    expect(screen.getByRole('alert')).toHaveTextContent('한 번에 2개까지')
  })

  it('크기 초과는 상한을 함께 알린다', async () => {
    const onChange = vi.fn()
    render(<FileUploader multiple maxSizeBytes={2048} onChange={onChange} />)
    const user = userEvent.setup()

    await user.upload(screen.getByLabelText('파일 선택'), [jpeg('small.jpg', 100), jpeg('big.jpg', 4096)])

    expect(onChange).toHaveBeenCalledWith([expect.objectContaining({ name: 'small.jpg' })])
    const alert = screen.getByRole('alert')
    expect(alert).toHaveTextContent('big.jpg')
    // 1MB 미만은 KB 로 말한다. `0.0MB` 는 "아무것도 올릴 수 없다" 로 읽힌다.
    expect(alert).toHaveTextContent('2KB')
  })

  it('사유가 섞여도 alert 는 하나다', () => {
    // 여러 개로 나누면 스크린 리더가 마지막 것만 읽거나 순서가 뒤섞인다.
    const { container } = render(
      <FileUploader multiple accept=".jpg" maxSizeBytes={2048} onChange={vi.fn()} />,
    )

    fireEvent.drop(container.querySelector('.ui-uploader__drop') as HTMLElement, {
      dataTransfer: { files: [file('doc.pdf', 'application/pdf'), jpeg('big.jpg', 4096)] },
    })

    const alerts = screen.getAllByRole('alert')
    expect(alerts).toHaveLength(1)
    expect(alerts[0]).toHaveTextContent('doc.pdf')
    expect(alerts[0]).toHaveTextContent('big.jpg')
  })
})

describe('로컬 미리보기', () => {
  it('showPreview 면 고른 이미지를 서버 없이 그린다', async () => {
    render(<FileUploader multiple showPreview accept=".jpg" onChange={vi.fn()} />)
    const user = userEvent.setup()

    await user.upload(screen.getByLabelText('파일 선택'), [jpeg('front.jpg')])

    expect(screen.getByAltText('front.jpg 미리보기')).toBeInTheDocument()
  })

  it('이미지가 아닌 것은 <img> 에 넣지 않는다', async () => {
    // pdf 를 <img> 에 넣으면 깨진 이미지가 되고, 사용자는 파일이 잘못됐다고 생각한다.
    render(<FileUploader multiple showPreview accept=".jpg,.pdf" onChange={vi.fn()} />)
    const user = userEvent.setup()

    await user.upload(screen.getByLabelText('파일 선택'), [
      jpeg('front.jpg'),
      file('slip.pdf', 'application/pdf'),
    ])

    expect(screen.getByAltText('front.jpg 미리보기')).toBeInTheDocument()
    expect(screen.queryByAltText('slip.pdf 미리보기')).not.toBeInTheDocument()
  })

  it('showPreview 가 없으면 그리지 않는다', async () => {
    // 자기 방식으로 미리보기를 그리는 화면에 두 번 그려지면 안 된다.
    render(<FileUploader showPreview={false} onChange={vi.fn()} />)
    const user = userEvent.setup()

    await user.upload(screen.getByLabelText('파일 선택'), [jpeg()])

    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })
})

describe('진행 표시', () => {
  it('진행률을 모르면 진행 중이라는 사실만 말한다', () => {
    render(<FileUploader uploading onChange={vi.fn()} />)

    expect(screen.getByRole('status')).toHaveTextContent('올리는 중')
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
  })

  it('진행률을 알면 값과 문구를 함께 준다', () => {
    // <progress> 값만 두면 스크린 리더 구현에 따라 읽히지 않는다.
    render(<FileUploader uploading progress={42} onChange={vi.fn()} />)

    expect(screen.getByRole('progressbar')).toHaveValue(42)
    expect(screen.getByRole('status')).toHaveTextContent('42% 올리는 중')
  })

  it('올리는 중에는 다시 고를 수 없다', () => {
    render(<FileUploader uploading onChange={vi.fn()} />)

    expect(screen.getByLabelText('파일 선택')).toBeDisabled()
  })
})

describe('드래그 앤 드롭', () => {
  it('multiple 이 아니면 여러 장을 놓아도 첫 장만 받는다', () => {
    // 조용히 마지막 것을 쓰면 사용자가 의도한 파일과 다른 것이 올라간다.
    const onChange = vi.fn()
    const { container } = render(<FileUploader onChange={onChange} />)
    const drop = container.querySelector('.ui-uploader__drop') as HTMLElement

    fireEvent.drop(drop, { dataTransfer: { files: [jpeg('first.jpg'), jpeg('second.jpg')] } })

    expect(onChange).toHaveBeenCalledWith([expect.objectContaining({ name: 'first.jpg' })])
  })
})

describe('validateFiles', () => {
  it('미디어 타입 규칙과 와일드카드를 모두 이해한다', () => {
    const png = file('a.png', 'image/png')
    const pdf = file('b.pdf', 'application/pdf')

    expect(validateFiles([png, pdf], { accept: 'image/*', maxSizeBytes: 1e9 })).toMatchObject({
      accepted: [png],
      rejected: [{ file: pdf, reason: 'TYPE' }],
    })
    expect(validateFiles([png, pdf], { accept: 'image/png', maxSizeBytes: 1e9 }).accepted).toEqual([
      png,
    ])
  })

  it('빈 파일은 크기 초과와 다른 사유로 거절한다', () => {
    // 서버도 FILE_EMPTY 와 FILE_TOO_LARGE 를 나눈다. 사용자가 할 일이 다르다.
    const empty = file('empty.jpg', 'image/jpeg', 0)

    expect(validateFiles([empty], { maxSizeBytes: 1024 }).rejected).toEqual([
      { file: empty, reason: 'EMPTY' },
    ])
  })

  it('미디어 타입을 모르는 파일은 통과시키고 서버가 보게 한다', () => {
    // 일부 OS 는 type 을 비워 준다. 앞에서 막으면 올릴 수 없는 파일이 되고,
    // 서버는 실제 내용으로 검사하므로 위험이 늘지 않는다.
    const unknown = file('scan', '')

    expect(validateFiles([unknown], { accept: 'image/jpeg', maxSizeBytes: 1024 }).accepted).toEqual(
      [unknown],
    )
  })
})
