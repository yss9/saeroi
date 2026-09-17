/**
 * `public/` 정적 자산의 절대경로(`/images/...`)를 현재 빌드의 `BASE_URL`
 * 기준으로 정규화한다.
 *
 * Vite 는 `import()`·`<img>` 의 정적 분석 가능한 참조만 `base` 를 적용해 재작성한다.
 * 코드에 문자열 리터럴로 박아 둔 `/images/...` 는 그 대상이 아니라서, `/test/` 빌드에서도
 * 그대로 `/images/...` 로 요청된다. nginx 는 `/test/` 프리픽스가 없는 요청을 운영
 * frontend 로 라우팅하므로, test 전용으로 추가된 이미지는 운영에 없어 깨진다
 * (`features/product/api/publicProductImageUrl.ts` 의 `/images/` 분기와 같은 문제).
 */
export function siteAssetUrl(path: string): string {
  return `${import.meta.env.BASE_URL.replace(/\/$/, '')}${path}`
}
