type Point = { x: number; y: number }

/** Keep the 74×24 route label inside the visible SVG and clear of marker halos. */
export function routeLabelPosition(origin: Point, markers: Point[], mapView: boolean, scale = 1, bounds = { left: 315, right: 840, top: 0, bottom: 620 }): Point | null {
  const left = mapView ? bounds.left : 0
  const right = mapView ? bounds.right : 1000
  const top = mapView ? bounds.top : 0
  const bottom = mapView ? bounds.bottom : 620
  const units = mapView ? 1 / scale : 1
  const clamp = (point: Point) => ({
    x: Math.min(Math.max(point.x, left + 40 * units), right - 40 * units),
    y: Math.min(Math.max(point.y, top + 16 * units), bottom - 16 * units),
  })
  for (const dy of [0, -20, 20]) {
    for (const dx of [0, -16, 16]) {
      const point = clamp({ x: origin.x + dx * units, y: origin.y + dy * units })
      if (Math.hypot(point.x - origin.x, point.y - origin.y) > 30 * units) continue
      if (markers.every((marker) => Math.abs(point.x - marker.x) >= 57 * units || Math.abs(point.y - marker.y) >= 30 * units)) {
        return point
      }
    }
  }
  return null
}
