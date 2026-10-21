import { expect, type APIRequestContext, type APIResponse } from '@playwright/test'
import { generatePrivateKey, privateKeyToAccount, type PrivateKeyAccount } from 'viem/accounts'

export type Wallet = PrivateKeyAccount

export interface Csrf {
  headerName: string
  token: string
}

/** 테스트마다 새 지갑을 쓴다. DB 를 되돌리지 않으므로 지갑이 겹치면 서로의 데이터를 밟는다. */
export function newWallet(): Wallet {
  return privateKeyToAccount(generatePrivateKey())
}

export async function expectOk(response: APIResponse): Promise<APIResponse> {
  expect(response.ok(), `${response.url()} → ${response.status()} ${await response.text()}`).toBe(true)
  return response
}

export async function fetchCsrf(request: APIRequestContext): Promise<Csrf> {
  const response = await expectOk(await request.get('/api/v1/auth/csrf'))
  const { data } = await response.json()

  return { headerName: data.headerName, token: data.token }
}

/**
 * 지갑 서명으로 로그인한다. SESSION 쿠키는 넘긴 request 의 쿠키 저장소에 남는다.
 * `page.context().request` 를 넘기면 브라우저도 같은 쿠키를 쓰므로 지갑 확장 없이 로그인된 화면을 열 수 있다.
 */
export async function login(request: APIRequestContext, wallet: Wallet = newWallet()) {
  const before = await fetchCsrf(request)
  const headers = { [before.headerName]: before.token }

  const issued = await expectOk(
    await request.post('/api/v1/auth/wallet/message', {
      headers,
      data: { walletAddress: wallet.address },
    }),
  )
  const message: string = (await issued.json()).data.message
  const signature = await wallet.signMessage({ message })

  await expectOk(
    await request.post('/api/v1/auth/wallet/login', {
      headers,
      data: { walletAddress: wallet.address, signature },
    }),
  )

  // 로그인하면 세션 ID 가 바뀌므로 CSRF 토큰을 다시 받는다.
  return { wallet, csrf: await fetchCsrf(request) }
}

/** 로그인한 request 로 보내는 API 호출. 상태를 바꾸는 요청에 CSRF 헤더를 붙인다. */
export class Api {
  constructor(
    readonly request: APIRequestContext,
    private readonly csrf: Csrf,
    readonly wallet: Wallet,
  ) {}

  get(path: string) {
    return this.request.get(path)
  }

  post(path: string, data?: unknown) {
    return this.request.post(path, { headers: this.headers(), data })
  }

  put(path: string, data?: unknown) {
    return this.request.put(path, { headers: this.headers(), data })
  }

  patch(path: string, data?: unknown) {
    return this.request.patch(path, { headers: this.headers(), data })
  }

  delete(path: string) {
    return this.request.delete(path, { headers: this.headers() })
  }

  /** multipart/form-data 로 보낸다. 가입 · 실행 접수처럼 명세가 multipart 인 API 에 쓴다. */
  postForm(path: string, form: Record<string, string>) {
    return this.request.post(path, { headers: this.headers(), multipart: form })
  }

  private headers() {
    return { [this.csrf.headerName]: this.csrf.token }
  }
}
