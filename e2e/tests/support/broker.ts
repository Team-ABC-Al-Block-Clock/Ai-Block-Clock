import { execFileSync } from 'node:child_process'

import { BROKER } from './runtimeEnv'
import { waitFor } from './runtime'

/**
 * 테스트용 RabbitMQ 의 관리 API(HTTP)로 큐를 들여다보고 메시지를 옮긴다.
 * 이름 · 인자는 실행 메시지 계약(backend/ai-block-clock/build/tdd/execution-rabbitmq/contract.md 1장)을 따른다.
 *
 * 큐에서 메시지를 "보기만" 하지 않는다 — 다시 넣기(requeue)는 quorum 큐의 배달 횟수를 올려
 * `x-delivery-limit=1` 에 걸리면 dead letter 로 가 버린다. 꺼낼 때는 늘 지운다(ack_requeue_false).
 */
const SUBMIT_QUEUE = 'abc.runs.submit'
const RUNS_EXCHANGE = 'abc.runs'
const RUNS_DLX = 'abc.runs.dlx'
const EVENTS_EXCHANGE = 'abc.events'
const VHOST = encodeURIComponent('/')

export interface BrokerMessage {
  payload: string
  payload_encoding: 'string' | 'base64'
  properties: Record<string, unknown> & { headers?: Record<string, unknown> }
}

export interface SubmitRun {
  execution_id: string
  mode: string
  workflow: unknown
  input: unknown
  comment: string | null
  mock: boolean
  files: string[]
  ref_files: Record<string, string[]>
  submitted_at: string
}

export interface RunEvent {
  execution_id: string
  mode: string
  attempt: number
  seq: number
  type: 'accepted' | 'progress' | 'succeeded' | 'failed' | 'rejected'
}

async function management(path: string, init: { method?: string; body?: unknown } = {}) {
  const response = await fetch(`${BROKER.managementUrl}/api${path}`, {
    method: init.method ?? 'GET',
    headers: {
      authorization: `Basic ${Buffer.from(`${BROKER.username}:${BROKER.password}`).toString('base64')}`,
      'content-type': 'application/json',
    },
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
  })

  if (!response.ok) {
    throw new Error(`관리 API ${init.method ?? 'GET'} ${path} → ${response.status} ${await response.text()}`)
  }

  const text = await response.text()

  return text ? JSON.parse(text) : null
}

const bodyOf = (message: BrokerMessage) =>
  message.payload_encoding === 'base64'
    ? Buffer.from(message.payload, 'base64').toString('utf8')
    : message.payload

async function take(queue: string, count: number): Promise<BrokerMessage[]> {
  return management(`/queues/${VHOST}/${encodeURIComponent(queue)}/get`, {
    method: 'POST',
    body: { count, ackmode: 'ack_requeue_false', encoding: 'auto' },
  })
}

async function publish(exchange: string, routingKey: string, message: BrokerMessage) {
  const { routed } = await management(`/exchanges/${VHOST}/${encodeURIComponent(exchange)}/publish`, {
    method: 'POST',
    body: {
      properties: message.properties,
      routing_key: routingKey,
      payload: message.payload,
      payload_encoding: message.payload_encoding,
    },
  })

  if (!routed) throw new Error(`${exchange} 로 보낸 메시지가 어느 큐에도 닿지 않았다`)
}

/** 큐에 쌓여 있는 메시지 수. 관리 API 통계라 몇 초 늦게 바뀐다. */
export async function readyCount(queue = SUBMIT_QUEUE): Promise<number> {
  return (await management(`/queues/${VHOST}/${encodeURIComponent(queue)}`)).messages_ready ?? 0
}

/**
 * 런타임이 내려가 있는 동안 `abc.runs.submit` 에서 기다리는 실행의 SubmitRun 을 꺼낸다(큐에서 지워진다).
 * 다른 실행의 것이 먼저 나오면 같은 모양으로 큐 뒤에 다시 넣는다.
 */
export async function takeSubmit(executionId: string, timeoutMs = 30_000) {
  let found: { message: BrokerMessage; body: SubmitRun } | undefined

  await waitFor(
    async () => {
      const [message] = await take(SUBMIT_QUEUE, 1)

      if (!message) return false

      const body = JSON.parse(bodyOf(message)) as SubmitRun

      if (body.execution_id === executionId) {
        found = { message, body }
        return true
      }

      await publish(RUNS_EXCHANGE, 'submit', message)
      return false
    },
    timeoutMs,
    `${SUBMIT_QUEUE} 에 실행 ${executionId} 의 SubmitRun 이 오지 않았다`,
  )

  return found!
}

/**
 * 브로커가 dead letter 할 때처럼 `abc.runs.dlx` 로 보낸다. 본문은 SubmitRun 그대로이고
 * `x-first-death-reason` 헤더를 붙인다(contract 8장). TTL 10분을 기다리지 않고 만료를 만든다.
 */
export async function deadLetter(message: BrokerMessage, reason: 'expired' | 'delivery_limit' | 'rejected') {
  await publish(RUNS_DLX, 'submit', {
    ...message,
    properties: {
      ...message.properties,
      headers: { ...(message.properties.headers ?? {}), 'x-first-death-reason': reason },
    },
  })
}

/**
 * 브로커를 멈춘다(docker pause). 연결은 끊기지 않지만 브로커가 아무 답도 하지 않는다 — 발행 confirm 이 오지 않는다.
 * 멈춘 동안에는 관리 API 도 답하지 않으니 부르지 않는다. 반드시 `resumeBroker` 로 되살린다.
 */
export function pauseBroker() {
  execFileSync('docker', ['pause', BROKER.container], { stdio: 'ignore' })
}

/** 멈춘 브로커를 되살린다. 멈춰 있지 않으면 아무것도 하지 않는다. */
export function resumeBroker() {
  const paused = execFileSync('docker', ['inspect', '-f', '{{.State.Paused}}', BROKER.container], {
    encoding: 'utf8',
  }).trim()

  if (paused === 'true') execFileSync('docker', ['unpause', BROKER.container], { stdio: 'ignore' })
}

/**
 * `abc.events` 에 붙여 런타임이 낸 RunEvent 를 엿보는 큐. 백엔드 큐와 따로 복사본을 받는다.
 * 쓰지 않으면 10분 뒤 브로커가 스스로 지운다(x-expires).
 */
export async function tapEvents() {
  const queue = `e2e.events.tap.${Date.now()}`
  const path = `/queues/${VHOST}/${encodeURIComponent(queue)}`

  await management(path, {
    method: 'PUT',
    body: { durable: true, auto_delete: false, arguments: { 'x-expires': 600_000 } },
  })
  await management(`/bindings/${VHOST}/e/${EVENTS_EXCHANGE}/q/${encodeURIComponent(queue)}`, {
    method: 'POST',
    body: { routing_key: 'run.#' },
  })

  const seen: RunEvent[] = []

  return {
    /** 지금까지 온 이벤트(앞서 꺼낸 것 포함) */
    async events(executionId: string): Promise<RunEvent[]> {
      const messages = await take(queue, 1000)

      seen.push(...messages.map((message) => JSON.parse(bodyOf(message)) as RunEvent))

      return seen.filter((event) => event.execution_id === executionId)
    },
    async close() {
      await management(path, { method: 'DELETE' })
    },
  }
}
