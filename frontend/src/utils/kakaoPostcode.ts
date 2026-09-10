const POSTCODE_SCRIPT_ID = 'kakao-postcode-script'
const POSTCODE_SCRIPT_URL = 'https://t1.kakaocdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js'

interface KakaoPostcodeData {
  zonecode: string
  address: string
  userSelectedType: 'R' | 'J'
  roadAddress: string
  jibunAddress: string
}

export interface PostcodeSelection {
  zonecode: string
  address: string
}

interface KakaoPostcodeInstance {
  open: () => void
}

type KakaoPostcodeConstructor = new (options: {
  oncomplete: (data: KakaoPostcodeData) => void
}) => KakaoPostcodeInstance

declare global {
  interface Window {
    kakao?: {
      Postcode?: KakaoPostcodeConstructor
    }
  }
}

let postcodeLoader: Promise<KakaoPostcodeConstructor> | null = null

function loadKakaoPostcode() {
  if (window.kakao?.Postcode) return Promise.resolve(window.kakao.Postcode)
  if (postcodeLoader) return postcodeLoader

  postcodeLoader = new Promise<KakaoPostcodeConstructor>((resolve, reject) => {
    const resolveConstructor = () => {
      if (window.kakao?.Postcode) resolve(window.kakao.Postcode)
      else reject(new Error('Kakao 주소 검색 서비스를 불러오지 못했습니다.'))
    }
    const rejectLoad = () => reject(new Error('Kakao 주소 검색 서비스를 불러오지 못했습니다.'))
    const existingScript = document.getElementById(POSTCODE_SCRIPT_ID) as HTMLScriptElement | null

    if (existingScript) {
      existingScript.addEventListener('load', resolveConstructor, { once: true })
      existingScript.addEventListener('error', rejectLoad, { once: true })
      return
    }

    const script = document.createElement('script')
    script.id = POSTCODE_SCRIPT_ID
    script.src = POSTCODE_SCRIPT_URL
    script.async = true
    script.addEventListener('load', resolveConstructor, { once: true })
    script.addEventListener('error', rejectLoad, { once: true })
    document.head.appendChild(script)
  }).catch((error) => {
    postcodeLoader = null
    throw error
  })

  return postcodeLoader
}

export async function openKakaoPostcode(onComplete: (selection: PostcodeSelection) => void) {
  const Postcode = await loadKakaoPostcode()

  new Postcode({
    oncomplete: (data) => {
      const selectedAddress = data.userSelectedType === 'R'
        ? data.roadAddress || data.address
        : data.jibunAddress || data.address
      onComplete({ zonecode: data.zonecode, address: selectedAddress })
    },
  }).open()
}
