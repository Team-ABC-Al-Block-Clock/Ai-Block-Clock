import { randomBytes } from 'node:crypto'
import { createServer, type Server } from 'node:http'

import { FAKE_CHAIN_PORT } from './runtimeEnv'

/**
 * 충전 확인이 묻는 SSAFY 체인 노드를 흉내 내는 JSON-RPC 서버.
 *
 * 충전은 사용자가 MetaMask 로 abcToken 을 보관 지갑에 보낸 거래를 백엔드가 체인에서 읽어 확인한다.
 * 테스트는 실제 체인에 거래를 만들 수 없으므로, 등록해 둔 거래 해시에만 "보관 지갑으로 간 abcToken 전송" 영수증을 돌려준다.
 * 백엔드는 `SSAFY_CHAIN_RPC_URL` 로 여기를 본다(playwright.config.ts, E2E_RUNTIME=1 일 때만).
 *
 * 아래 주소는 백엔드 기본값과 같아야 한다 — application.yaml `payment.charge.custody-address`,
 * blockchain.yml `ssafy.sto.token.contract-address`, 체인 ID 31221.
 */
const CHAIN_ID_HEX = '0x79f5'
const TOKEN = '0x56dc097c14836473f4ad4bd675a3d01be5e4c63c'
const CUSTODY = '0x64e72a170a7a36ce09806190ed02085fe23cb6b6'
const TRANSFER_TOPIC = '0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef'
const BLOCK = '0x64'

interface Transfer {
  from: string
  /** 토큰 최소 단위(10^18 = 1 ABC) */
  rawAmount: bigint
}

const transfers = new Map<string, Transfer>()
let started: Promise<Server> | undefined

const topicOf = (address: string) => `0x${'0'.repeat(24)}${address.slice(2).toLowerCase()}`

function transactionOf(hash: string, transfer: Transfer) {
  return {
    hash,
    from: transfer.from.toLowerCase(),
    to: TOKEN,
    chainId: CHAIN_ID_HEX,
    blockNumber: BLOCK,
    nonce: '0x0',
    value: '0x0',
    gas: '0x29254',
    gasPrice: '0x0',
    input: '0x',
    v: '0xf40e',
    r: '0x1',
    s: '0x1',
    type: '0x0',
  }
}

function receiptOf(hash: string, transfer: Transfer) {
  return {
    transactionHash: hash,
    blockNumber: BLOCK,
    status: '0x1',
    from: transfer.from.toLowerCase(),
    to: TOKEN,
    gasUsed: '0x0',
    cumulativeGasUsed: '0x0',
    logs: [
      {
        address: TOKEN,
        topics: [TRANSFER_TOPIC, topicOf(transfer.from), topicOf(CUSTODY)],
        data: `0x${transfer.rawAmount.toString(16).padStart(64, '0')}`,
      },
    ],
  }
}

function answer(method: string, params: unknown[]): unknown {
  const hash = String(params?.[0] ?? '').toLowerCase()
  const transfer = transfers.get(hash)

  switch (method) {
    case 'eth_getTransactionByHash':
      return transfer ? transactionOf(hash, transfer) : null
    case 'eth_getTransactionReceipt':
      return transfer ? receiptOf(hash, transfer) : null
    case 'eth_blockNumber':
      return BLOCK
    default:
      throw new Error(`가짜 체인은 ${method} 를 모른다`)
  }
}

function start(): Promise<Server> {
  const server = createServer((req, res) => {
    let body = ''

    req.on('data', (chunk) => (body += chunk))
    req.on('end', () => {
      const call = JSON.parse(body) as { id: number; method: string; params: unknown[] }
      let reply: object

      try {
        reply = { jsonrpc: '2.0', id: call.id, result: answer(call.method, call.params) }
      } catch (error) {
        reply = { jsonrpc: '2.0', id: call.id, error: { code: -32601, message: String(error) } }
      }

      res.writeHead(200, { 'Content-Type': 'application/json' })
      res.end(JSON.stringify(reply))
    })
  })

  return new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(FAKE_CHAIN_PORT, '127.0.0.1', () => {
      // 워커가 끝날 때 이 서버가 붙잡지 않게 한다.
      server.unref()
      resolve(server)
    })
  })
}

/**
 * `from` 이 보관 지갑으로 abcToken 을 보낸 거래를 하나 만들어 해시를 돌려준다.
 * 금액은 ABC 단위 문자열(소수 6자리까지).
 */
export async function recordDeposit(from: string, amount: string): Promise<string> {
  started ??= start()
  await started

  const [whole, fraction = ''] = amount.split('.')
  const rawAmount = BigInt(whole) * 10n ** 18n + BigInt(fraction.padEnd(18, '0') || '0')
  const hash = `0x${randomBytes(32).toString('hex')}`

  transfers.set(hash, { from, rawAmount })

  return hash
}
