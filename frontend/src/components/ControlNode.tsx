import type { ControlKind } from '@/api/workflows'
import { controlKindName } from '@/lib/format'

import CanvasNode, { type CanvasNodeProps } from './CanvasNode'

/**
 * 종류마다 정해진 모양과 포트. 일하는 노드(원)와 한눈에 갈리는 것이 목적이다.
 *
 * 시작·종료는 흐름의 끝점이라 알약, 분기는 갈림길이라 육각이다.
 *
 * 포트는 흐름이 드나드는 쪽만 낸다. 시작 앞과 종료 뒤에는 아무것도 올 수 없다.
 */
const KINDS = {
  BRANCH: { shape: 'hex', ports: ['in', 'out'] },
  START: { shape: 'pill', ports: ['out'] },
  END: { shape: 'pill', ports: ['in'] },
} as const satisfies Record<ControlKind, Pick<CanvasNodeProps, 'shape' | 'ports'>>

/**
 * 셋이 한 색을 쓴다. 종류는 모양이 이미 가르고, 색은 "일하는 노드가 아니다" 만 말하면 된다.
 *
 * 초록·자주로 갈라 두면 성공·실패로 잘못 읽힌다. 작업 카드가 흰 반투명이라 라일락 한 색을
 * 채우는 것으로 무리가 갈린다.
 */
const FILL = { on: 'rgba(132,100,196,.7)', off: 'rgba(132,100,196,.56)' }

export interface ControlNodeProps extends Omit<
  CanvasNodeProps,
  'shape' | 'ports' | 'background' | 'name' | 'sublabel' | 'mine'
> {
  kind: ControlKind
  /** 분기의 조건 요약. 알약은 한 줄뿐이라 받지 않는다 */
  sublabel?: string
  selected?: boolean
}

/**
 * 제어·입출력 노드. 모양과 포트만 정해 `CanvasNode` 에 넘긴다.
 *
 * 끌고 놓기·선 잇기·모션은 일하는 노드와 똑같아야 해서 여기서 다시 만들지 않는다.
 * `mine` 은 주지 않는다 — 누구의 것도 아니라 MY 칩도 판매 경고도 붙지 않는다.
 */
export default function ControlNode({
  kind,
  sublabel = '',
  selected = false,
  ...rest
}: ControlNodeProps) {
  return (
    <CanvasNode
      {...rest}
      {...KINDS[kind]}
      name={controlKindName(kind)}
      sublabel={sublabel}
      // 라일락 위라 기본 흰색(.9)이 흐리게 눕는다. 글자만 또렷하게 둔다.
      nameColor="#fff"
      sublabelColor="rgba(255,255,255,.88)"
      background={selected ? FILL.on : FILL.off}
      mine={null}
    />
  )
}
