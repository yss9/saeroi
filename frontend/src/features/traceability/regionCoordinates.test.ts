import { describe, expect, it } from 'vitest'
import { resolveRegionCoordinates } from './regionCoordinates.ts'

const regions = [
  ['서울', '서울특별시', 37.5665, 126.978],
  ['부산', '부산광역시', 35.1796, 129.0756],
  ['대구', '대구광역시', 35.8714, 128.6014],
  ['인천', '인천광역시', 37.4563, 126.7052],
  ['광주', '광주광역시', 35.1595, 126.8526],
  ['대전', '대전광역시', 36.3504, 127.3845],
  ['울산', '울산광역시', 35.5384, 129.3114],
  ['세종', '세종특별자치시', 36.48, 127.289],
  ['경기', '경기도', 37.4138, 127.5183],
  ['강원', '강원특별자치도', 37.8228, 128.1555],
  ['충북', '충청북도', 36.6357, 127.4917],
  ['충남', '충청남도', 36.6588, 126.6728],
  ['전북', '전북특별자치도', 35.8203, 127.1088],
  ['전남', '전라남도', 34.8161, 126.4629],
  ['경북', '경상북도', 36.4919, 128.8889],
  ['경남', '경상남도', 35.4606, 128.2132],
  ['제주', '제주특별자치도', 33.4996, 126.5312],
] as const

describe('delivery region coordinates', () => {
  it.each(regions)('matches %s and its full name', (short, full, latitude, longitude) => {
    const expected = { latitude, longitude }
    expect(resolveRegionCoordinates(short)).toEqual(expected)
    expect(resolveRegionCoordinates(full)).toEqual(expected)
    expect(resolveRegionCoordinates(`  ${full} 지역 주소  `)).toEqual(expected)
  })

  it.each([['강원도', '강원'], ['전라북도', '전북'], ['제주도', '제주']])(
    'matches legacy name %s', (legacy, short) => {
      expect(resolveRegionCoordinates(legacy)).toEqual(resolveRegionCoordinates(short))
    },
  )

  it.each([null, '', '  ', '알 수 없는 지역', '경남아님', '서울시외'])('does not invent coordinates for %s', (value) => {
    expect(resolveRegionCoordinates(value)).toBeNull()
  })
})
