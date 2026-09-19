import { NODE_SIZE } from './canvasWorkflow'

export interface Point {
  x: number
  y: number
}

/** 카드 둘레에서 선이 띄우는 거리(px). 막히면 좁혀서 재탐색. */
const CLEARANCES = [12, 6, 2]
/** 포트에서 곧게 빠져나가는 길이(px). 막히면 줄여서 재탐색. */
const LEADS = [30, 20]
/** 모든 카드 바깥을 도는 길까지의 거리(px). 사방이 막혀도 돌아갈 길을 남김. */
const RING = 60
/** 꺾이는 자리의 둥글기(px). */
const ROUND = 18
/** 한 번 꺾을 때 더하는 값(px). 클수록 조금 돌더라도 덜 꺾는 길을 선택한다. */
const TURN = 250
/** 겉면에 얹힌 점이 반올림으로 안쪽에 떨어지지 않게 두는 여유(px). */
const EPS = 0.25

interface Box {
  x0: number
  y0: number
  x1: number
  y1: number
}

/** 카드를 감싸는 네모. 이 바깥이면 원 밖인 것이 보장된다. */
const boxOf = (center: Point, clear: number): Box => {
  const half = NODE_SIZE / 2 + clear
  return { x0: center.x - half, y0: center.y - half, x1: center.x + half, y1: center.y + half }
}

/** 축에 나란한 선분이 네모 속을 지나는지. 겉면을 스치는 것은 제외. */
function crosses(box: Box, a: Point, b: Point): boolean {
  const [lo, hi] = a.x === b.x ? [a.y, b.y] : [a.x, b.x]
  const [near, far] = lo < hi ? [lo, hi] : [hi, lo]
  if (a.x === b.x) {
    return a.x > box.x0 + EPS && a.x < box.x1 - EPS && far > box.y0 + EPS && near < box.y1 - EPS
  }
  return a.y > box.y0 + EPS && a.y < box.y1 - EPS && far > box.x0 + EPS && near < box.x1 - EPS
}

const open = (boxes: Box[], a: Point, b: Point) => boxes.every((box) => !crosses(box, a, b))

/** 격자의 줄 좌표. 같은 값은 하나로. */
const lines = (values: number[]) =>
  [...new Set(values.map((v) => Math.round(v * 10) / 10))].sort((a, b) => a - b)

/**
 * 카드를 피해 포트에서 포트로 가는 직교 경로. 못 찾으면 `null`.
 *
 * 카드 모서리 바깥에 격자를 깔고 A* 로 찾는다. 꺾을 때마다 값을 더해 되도록 곧게.
 */
export function routeEdge(from: Point, to: Point, centers: Point[]): Point[] | null {
  for (const clear of CLEARANCES) {
    for (const lead of [...LEADS, clear]) {
      // 리드가 더 짧으면 포트에서 나가는 구간이 상자 안에서 끝난다. 지금 상수로는 안 걸린다.
      if (lead < clear) continue
      const found = search(from, to, centers, clear, lead)
      if (found) return found
    }
  }
  return null
}

function search(
  from: Point,
  to: Point,
  centers: Point[],
  clear: number,
  lead: number,
): Point[] | null {
  const boxes = centers.map((center) => boxOf(center, clear))
  // 격자와 같은 눈금에 올려야 시작·도착을 격자에서 찾는다.
  const snap = (v: number) => Math.round(v * 10) / 10
  // 두 포트가 가까우면 양쪽 곧은 구간이 서로를 지나쳐 꼬리가 남는다. 간격의 절반까지만 쓴다.
  const span = to.x - from.x
  const reach = span > 0 ? Math.max(clear, Math.min(lead, span / 2)) : lead
  const start = { x: snap(from.x + reach), y: snap(from.y) }
  const goal = { x: snap(to.x - reach), y: snap(to.y) }
  // 곧은 구간은 제 카드 속을 지나므로 그것만 예외.
  const half = NODE_SIZE / 2
  const mine = (box: Box, center: Point) =>
    Math.abs((box.x0 + box.x1) / 2 - center.x) < 0.5 &&
    Math.abs((box.y0 + box.y1) / 2 - center.y) < 0.5
  const others = (center: Point) => boxes.filter((box) => !mine(box, center))
  if (!open(others({ x: from.x - half, y: from.y }), from, start)) return null
  if (!open(others({ x: to.x + half, y: to.y }), goal, to)) return null

  const xs = lines([
    start.x,
    goal.x,
    ...boxes.flatMap((b) => [b.x0 - 1, b.x1 + 1]),
    Math.min(...boxes.map((b) => b.x0), start.x, goal.x) - RING,
    Math.max(...boxes.map((b) => b.x1), start.x, goal.x) + RING,
  ])
  const ys = lines([
    start.y,
    goal.y,
    ...boxes.flatMap((b) => [b.y0 - 1, b.y1 + 1]),
    Math.min(...boxes.map((b) => b.y0), start.y, goal.y) - RING,
    Math.max(...boxes.map((b) => b.y1), start.y, goal.y) + RING,
  ])

  const at = (xi: number, yi: number): Point => ({ x: xs[xi], y: ys[yi] })
  const inside = (p: Point) =>
    boxes.some((b) => p.x > b.x0 + EPS && p.x < b.x1 - EPS && p.y > b.y0 + EPS && p.y < b.y1 - EPS)
  const si = xs.indexOf(start.x)
  const sj = ys.indexOf(start.y)
  const gi = xs.indexOf(goal.x)
  const gj = ys.indexOf(goal.y)
  if (si < 0 || sj < 0 || gi < 0 || gj < 0) return null

  const W = xs.length
  const key = (xi: number, yi: number) => yi * W + xi
  // 엄밀히는 "(칸, 방향)" 이 상태다. 칸만 쓰면 최소 비용이 아닐 수 있으나 TURN 이 커서
  // 카드 몇 장 규모에서는 경로가 같다.
  const best = new Map<number, number>()
  const came = new Map<number, number>()
  const guess = (xi: number, yi: number) => Math.abs(xs[xi] - goal.x) + Math.abs(ys[yi] - goal.y)
  // 힙을 둘 만큼 크지 않아 매번 훑어 꺼낸다.
  const queue: { xi: number; yi: number; cost: number; rank: number }[] = [
    { xi: si, yi: sj, cost: 0, rank: guess(si, sj) },
  ]
  best.set(key(si, sj), 0)

  while (queue.length > 0) {
    let pick = 0
    for (let i = 1; i < queue.length; i++) if (queue[i].rank < queue[pick].rank) pick = i
    const here = queue.splice(pick, 1)[0]
    if (here.xi === gi && here.yi === gj) break
    if ((best.get(key(here.xi, here.yi)) ?? Infinity) < here.cost) continue

    const from0 = came.get(key(here.xi, here.yi))
    const wasVertical = from0 === undefined ? false : Math.floor(from0 / W) !== here.yi
    for (const [dx, dy] of [
      [1, 0],
      [-1, 0],
      [0, 1],
      [0, -1],
    ]) {
      const xi = here.xi + dx
      const yi = here.yi + dy
      if (xi < 0 || yi < 0 || xi >= W || yi >= ys.length) continue
      const next = at(xi, yi)
      if (inside(next) || !open(boxes, at(here.xi, here.yi), next)) continue
      const turned = from0 !== undefined && wasVertical !== (dy !== 0)
      const step = Math.abs(next.x - xs[here.xi]) + Math.abs(next.y - ys[here.yi])
      const cost = here.cost + step + (turned ? TURN : 0)
      if (cost >= (best.get(key(xi, yi)) ?? Infinity)) continue
      best.set(key(xi, yi), cost)
      came.set(key(xi, yi), key(here.xi, here.yi))
      queue.push({ xi, yi, cost, rank: cost + guess(xi, yi) })
    }
  }

  if (!best.has(key(gi, gj))) return null
  const back: Point[] = []
  for (let k: number | undefined = key(gi, gj); k !== undefined; k = came.get(k)) {
    back.push({ x: xs[k % W], y: ys[Math.floor(k / W)] })
    if (k === key(si, sj)) break
  }
  back.reverse()

  const straight = fold(back)

  // 가운데 정렬 전에 접는다. 포트와 곧은 구간 끝이 따로 남으면 구간 번호가 밀린다.
  const route = fold([from, ...straight, to])
  const startBox = boxes.filter((box) => mine(box, { x: from.x - half, y: from.y }))
  const endBox = boxes.filter((box) => mine(box, { x: to.x + half, y: to.y }))
  const passable = (a: Point, b: Point, at: number) => {
    const skip = at === 0 ? startBox : at === route.length - 2 ? endBox : []
    return boxes.every((box) => skip.includes(box) || !crosses(box, a, b))
  }

  // 두 가로 구간의 한가운데에서 연결선을 세로로 꺾는다.
  // 한가운데가 불가한 경우, 가운데와 가장 가까운 지점을 선택한다.
  for (let i = 1; i + 2 < route.length; i++) {
    const [a, b, c, d] = route.slice(i - 1, i + 3)
    if (!same(b.x, c.x) || !same(a.y, b.y) || !same(c.y, d.y)) continue
    const middle = (a.x + d.x) / 2
    const keeps = (want: number) => {
      if (
        i - 1 === 0 &&
        (Math.sign(want - a.x) !== Math.sign(b.x - a.x) || Math.abs(want - a.x) < reach)
      )
        return false
      if (
        i + 1 === route.length - 2 &&
        (Math.sign(d.x - want) !== Math.sign(d.x - c.x) || Math.abs(d.x - want) < reach)
      )
        return false
      return true
    }
    for (let step = 0; step <= 10; step++) {
      const want = middle + ((b.x - middle) * step) / 10
      if (!keeps(want)) continue
      const nb = { x: want, y: b.y }
      const nc = { x: want, y: c.y }
      if (!passable(a, nb, i - 1) || !passable(nb, nc, i) || !passable(nc, d, i + 1)) continue
      // route 는 from/to 를 그대로 품는다. 원본 대신 새 점으로 바꿔 끼운다.
      route[i] = nb
      route[i + 1] = nc
      break
    }
  }

  // 자리를 옮기면서 한 줄이 됐을 수 있다.
  return fold(route)
}

/** 같은 줄인지. 격자에 맞추며 반올림돼 값이 딱 맞지 않는다. */
const same = (u: number, v: number) => Math.abs(u - v) < EPS * 4

/** 한 줄로 이어지는 가운데 점을 접는다. */
function fold(points: Point[]): Point[] {
  const kept: Point[] = []
  for (const p of points) {
    const [a, b] = [kept[kept.length - 2], kept[kept.length - 1]]
    if (a && b && ((same(a.x, b.x) && same(b.x, p.x)) || (same(a.y, b.y) && same(b.y, p.y))))
      kept.pop()
    kept.push(p)
  }
  return kept
}

/** 꺾이는 자리를 둥글려 그린다. */
export function roundedPath(points: Point[], radius = ROUND): string {
  if (points.length < 2) return ''
  const len = (a: Point, b: Point) => Math.hypot(b.x - a.x, b.y - a.y)
  const trim = (v: number) => Math.round(v * 10) / 10
  let d = `M${trim(points[0].x)} ${trim(points[0].y)}`
  for (let i = 1; i < points.length - 1; i++) {
    const [prev, here, next] = [points[i - 1], points[i], points[i + 1]]
    const r = Math.min(radius, len(prev, here) / 2, len(here, next) / 2)
    if (r < 1) continue
    const back = {
      x: here.x + ((prev.x - here.x) / len(prev, here)) * r,
      y: here.y + ((prev.y - here.y) / len(prev, here)) * r,
    }
    const fore = {
      x: here.x + ((next.x - here.x) / len(here, next)) * r,
      y: here.y + ((next.y - here.y) / len(here, next)) * r,
    }
    d += ` L${trim(back.x)} ${trim(back.y)} Q${trim(here.x)} ${trim(here.y)} ${trim(fore.x)} ${trim(fore.y)}`
  }
  const end = points[points.length - 1]
  return `${d} L${trim(end.x)} ${trim(end.y)}`
}
