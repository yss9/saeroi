// blockchain 저장소의 artifact JSON 에서 프론트용 ABI 파일을 생성한다.
//
// 왜 생성하는가 — 손으로 옮겨 적었더니 실제로 틀렸다.
// MaterialToken 에 없는 `verifyProcessing` 을 넣어두었는데, 타입 검사·빌드·단위 테스트가
// 전부 통과했다. 실제 호출 시점에야 드러났을 오류다.
//
// 전체 JSON 을 그대로 번들에 넣지 않는 이유는 크기다 (컨트랙트당 1,000줄 이상).
// 사람이 읽는 형식(human-readable ABI)으로 줄여서 담되, **문자열은 사람이 쓰지 않는다.**
//
// 실행
//   npm run gen:abi
//
// blockchain 저장소가 형제 폴더에 있어야 한다. 없으면 안내만 하고 종료한다 —
// CI 나 다른 사람의 환경에서 빌드를 깨뜨리지 않기 위해서다. 생성 결과물은 커밋되므로
// 이 스크립트를 돌리지 않아도 앱은 동작한다.

import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const ABI_DIR = [resolve(here, '../../blockchain/abi'), resolve(here, '../../S15P21E104_blockchain/abi')]
  .find((candidate) => existsSync(candidate)) ?? resolve(here, '../../blockchain/abi')
const OUT_DIR = resolve(here, '../src/config/abi')

interface AbiInput {
  name: string
  type: string
  indexed?: boolean
  components?: AbiInput[]
}

interface AbiEntry {
  type: string
  name?: string
  inputs?: AbiInput[]
  outputs?: AbiInput[]
  stateMutability?: string
}

/** tuple 타입을 `tuple(uint256 id, address owner)` 형태로 편다. */
function formatType(input: AbiInput): string {
  if (!input.type.startsWith('tuple')) return input.type
  const inner = (input.components ?? []).map(formatParam).join(', ')
  // tuple[] 같은 배열 접미사를 보존한다
  const suffix = input.type.slice('tuple'.length)
  return `tuple(${inner})${suffix}`
}

function formatParam(input: AbiInput): string {
  const parts = [formatType(input)]
  if (input.indexed) parts.push('indexed')
  if (input.name) parts.push(input.name)
  return parts.join(' ')
}

function formatEntry(entry: AbiEntry): string | null {
  const inputs = (entry.inputs ?? []).map(formatParam).join(', ')

  if (entry.type === 'event') {
    return `event ${entry.name}(${inputs})`
  }

  if (entry.type === 'function') {
    const parts = [`function ${entry.name}(${inputs})`]
    // view / pure 는 남긴다. nonpayable 은 기본값이라 생략한다.
    if (entry.stateMutability === 'view' || entry.stateMutability === 'pure') {
      parts.push(entry.stateMutability)
    }
    if (entry.stateMutability === 'payable') parts.push('payable')
    const outputs = (entry.outputs ?? []).map(formatParam).join(', ')
    if (outputs) parts.push(`returns (${outputs})`)
    return parts.join(' ')
  }

  // constructor · error · fallback 은 프론트에서 쓰지 않는다.
  return null
}

const CONTRACTS = [
  { file: 'MaterialToken', constant: 'MATERIAL_TOKEN_ABI', out: 'materialToken.abi.ts' },
  { file: 'Marketplace', constant: 'MARKETPLACE_ABI', out: 'marketplace.abi.ts' },
  { file: 'ProcessingRegistry', constant: 'PROCESSING_REGISTRY_ABI', out: 'processingRegistry.abi.ts' },
  { file: 'ProductionRegistry', constant: 'PRODUCTION_REGISTRY_ABI', out: 'productionRegistry.abi.ts' },
]

if (!existsSync(ABI_DIR)) {
  console.error(`blockchain 저장소를 찾을 수 없습니다: ${ABI_DIR}`)
  console.error('형제 폴더로 clone 한 뒤 다시 실행하세요.')
  console.error('  git clone -b blockchain/develop <repo> blockchain')
  process.exit(1)
}

for (const { file, constant, out } of CONTRACTS) {
  const raw = JSON.parse(readFileSync(`${ABI_DIR}/${file}.json`, 'utf8')) as AbiEntry[]
  const signatures = raw.map(formatEntry).filter((line): line is string => line !== null)

  const body = signatures.map((line) => `  ${JSON.stringify(line)},`).join('\n')
  const content = `// ⚠️ 이 파일은 자동 생성됩니다. 직접 수정하지 마세요.
//
//   생성 명령 : npm run gen:abi
//   원본      : blockchain/abi/${file}.json  (blockchain/master 가 Source of Truth)
//
// 컨트랙트가 재배포되어 ABI 가 바뀌면 blockchain 저장소를 pull 한 뒤 위 명령을 다시 실행합니다.

export const ${constant} = [
${body}
] as const
`
  writeFileSync(`${OUT_DIR}/${out}`, content, 'utf8')
  console.log(`${out.padEnd(30)} ${signatures.length}개 시그니처`)
}
