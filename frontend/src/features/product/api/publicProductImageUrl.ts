import { env } from '../../../config/env.ts'

/** 서버가 제공한 공개 URL만 해석. storage_key에서 주소를 만들지 않는다. */
export function publicProductImageUrl(url: string, apiBase = env.apiBaseUrl, siteBase = import.meta.env.BASE_URL) {
  if (url.startsWith('/api/')) return `${apiBase.replace(/\/$/, '')}${url.slice(4)}`
  if (url.startsWith('/images/')) return `${siteBase.replace(/\/$/, '')}${url}`
  return url
}
