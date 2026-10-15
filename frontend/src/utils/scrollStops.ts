export function adjacentScrollStop(stops: number[], current: number, direction: number) {
  const tolerance = 12
  return direction > 0
    ? stops.find((stop) => stop > current + tolerance)
    : [...stops].reverse().find((stop) => stop < current - tolerance)
}
