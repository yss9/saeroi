const REGION_COORDINATES: Record<string, { latitude: number; longitude: number }> = {
  서울특별시: { latitude: 37.5665, longitude: 126.978 },
  부산광역시: { latitude: 35.1796, longitude: 129.0756 },
  대구광역시: { latitude: 35.8714, longitude: 128.6014 },
  인천광역시: { latitude: 37.4563, longitude: 126.7052 },
  광주광역시: { latitude: 35.1595, longitude: 126.8526 },
  대전광역시: { latitude: 36.3504, longitude: 127.3845 },
  울산광역시: { latitude: 35.5384, longitude: 129.3114 },
  세종특별자치시: { latitude: 36.48, longitude: 127.289 },
  경기도: { latitude: 37.4138, longitude: 127.5183 },
  강원특별자치도: { latitude: 37.8228, longitude: 128.1555 },
  충청북도: { latitude: 36.6357, longitude: 127.4917 },
  충청남도: { latitude: 36.6588, longitude: 126.6728 },
  전북특별자치도: { latitude: 35.8203, longitude: 127.1088 },
  전라남도: { latitude: 34.8161, longitude: 126.4629 },
  경상북도: { latitude: 36.4919, longitude: 128.8889 },
  경상남도: { latitude: 35.4606, longitude: 128.2132 },
  제주특별자치도: { latitude: 33.4996, longitude: 126.5312 },
}

const REGION_ALIASES: Record<string, string> = {
  서울: '서울특별시', 부산: '부산광역시', 대구: '대구광역시',
  인천: '인천광역시', 광주: '광주광역시', 대전: '대전광역시',
  울산: '울산광역시', 세종: '세종특별자치시', 경기: '경기도',
  강원: '강원특별자치도', 강원도: '강원특별자치도',
  충북: '충청북도', 충남: '충청남도',
  전북: '전북특별자치도', 전라북도: '전북특별자치도',
  전남: '전라남도', 경북: '경상북도', 경남: '경상남도',
  제주: '제주특별자치도', 제주도: '제주특별자치도',
}

/** Match the first address token; keep the original display name unchanged. */
export function resolveRegionCoordinates(locationName: string | null) {
  const token = locationName?.trim().split(/\s+/, 1)[0]
  if (!token) return null
  const canonicalName = REGION_ALIASES[token] ?? token
  return REGION_COORDINATES[canonicalName] ?? null
}
