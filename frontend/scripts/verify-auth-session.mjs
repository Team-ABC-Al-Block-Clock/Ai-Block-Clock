/**
 * 인증 세션(`src/stores/authStore.ts`)의 상태 전이를 확인한다. `npm run verify:auth`
 *
 * 확인하는 것은 **응답이 늦게 도착했을 때**다. 복구·로그인은 요청을 두 번(토큰 →
 * 프로필) 기다리고, 그 사이에 세션이 갈릴 수 있다. 늦게 도착한 응답이 이미 지나간
 * 세션을 되살리면 로그인 직후 튕기거나 죽은 세션으로 보호 화면이 열린다.
 * 브라우저에서 손으로 누르면 타이밍이 맞아야 재현되므로 여기서 응답 순서를 직접 정한다.
 *
 * 스토어를 소스 그대로 로드하고 `@/api/*` 와 `@/lib/tokenStorage` 만 대역으로
 * 갈아끼운다(`auth-session-stub.js`). vite 는 이미 devDependency 라서 새로 설치할
 * 것이 없다.
 *
 * ponytail: 팀에 vitest 가 들어오면 이 시나리오들을 `authStore.test.ts` 로 옮기고
 * 이 스크립트를 지우세요. 여기 있는 건 러너가 없어서지, 이게 더 나은 형태라서가 아닙니다.
 */
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'

const ROOT = fileURLToPath(new URL('..', import.meta.url))
const STUB = fileURLToPath(new URL('./auth-session-stub.js', import.meta.url))
const STORE = '/src/stores/authStore.ts'

// alias 는 `enforce: 'pre'` 플러그인보다 먼저 돌아서, 대역도 alias 로 끼운다.
// 구체적인 것을 먼저 둔다 — `@` 가 앞에 오면 전부 실제 모듈로 간다.
const server = await createServer({
  root: ROOT,
  configFile: false,
  logLevel: 'error',
  appType: 'custom',
  server: { middlewareMode: true },
  resolve: {
    alias: [
      { find: '@/api/client', replacement: STUB },
      { find: '@/api/auth', replacement: STUB },
      { find: '@/lib/tokenStorage', replacement: STUB },
      { find: '@', replacement: `${ROOT}src` },
    ],
  },
})

const PROFILE = { userId: 'u1', nickname: '이싸피', walletAddress: '0xaaa' }

/** 손으로 풀어 주는 promise — 시나리오가 응답 도착 시점을 정한다. */
function deferred() {
  let resolve
  const promise = new Promise((res) => {
    resolve = res
  })
  return { promise, resolve }
}

/** 대기 중인 마이크로태스크를 흘려보낸다. */
const settle = () => new Promise((r) => setTimeout(r, 0))

let passed = 0
let failed = 0

function check(name, got, want) {
  if (got === want) {
    console.log(`  ✓ ${name}`)
    passed += 1
  } else {
    console.log(`  ✗ ${name} — got ${JSON.stringify(got)}, want ${JSON.stringify(want)}`)
    failed += 1
  }
}

/**
 * 시나리오마다 스토어 모듈을 새로 로드한다.
 * 세션 번호(epoch)가 모듈 전역이라 시나리오끼리 공유되면 결과가 섞인다.
 */
async function freshStore(harness) {
  globalThis.__authHarness = { token: null, ...harness }
  server.moduleGraph.invalidateAll()
  const { useAuthStore } = await server.ssrLoadModule(STORE)
  return useAuthStore
}

// ── 1 ───────────────────────────────────────────────────────────
console.log('\n[1] 부팅 복구가 늦게 실패하는 동안 로그인이 성공한다')
{
  const reissue = deferred()
  const useAuthStore = await freshStore({
    restoreSession: () => reissue.promise,
    loginWithWallet: async () => ({ accessToken: 'fresh-token' }),
    getMyProfile: async () => PROFILE,
  })
  const store = useAuthStore.getState()

  const restoring = store.restore()
  await settle()
  check('복구 중에는 restoring', useAuthStore.getState().status, 'restoring')

  await store.signIn({ walletAddress: '0xaaa', signature: '0xsig' })
  check('로그인 직후 authenticated', useAuthStore.getState().status, 'authenticated')

  reissue.resolve(false) // 이제서야 재발급 실패가 도착한다
  await restoring
  await settle()
  check('늦은 실패가 로그인을 덮지 않는다', useAuthStore.getState().status, 'authenticated')
  check('로그인 토큰이 살아 있다', globalThis.__authHarness.token, 'fresh-token')
}

// ── 2 ───────────────────────────────────────────────────────────
console.log('\n[2] 복구 중 401 로 세션이 끊긴 뒤 프로필이 도착한다')
{
  const profile = deferred()
  const useAuthStore = await freshStore({
    restoreSession: async () => true,
    getMyProfile: () => profile.promise,
  })
  const store = useAuthStore.getState()

  const restoring = store.restore()
  await settle()
  check('프로필 대기 중에는 restoring', useAuthStore.getState().status, 'restoring')

  globalThis.__authHarness.onAuthFailure() // 인터셉터가 회복 불가능한 401 을 알려 온다
  check('401 직후 unauthenticated', useAuthStore.getState().status, 'unauthenticated')

  profile.resolve(PROFILE)
  await restoring
  await settle()
  check('늦은 프로필이 세션을 되살리지 않는다', useAuthStore.getState().status, 'unauthenticated')
  check('토큰이 남지 않는다', globalThis.__authHarness.token, null)
}

// ── 3 ───────────────────────────────────────────────────────────
console.log('\n[3] 로그인 도중 세션이 끊긴다')
{
  const login = deferred()
  const useAuthStore = await freshStore({
    restoreSession: async () => false,
    loginWithWallet: () => login.promise,
    getMyProfile: async () => PROFILE,
  })
  const store = useAuthStore.getState()

  const signing = store.signIn({ walletAddress: '0xaaa', signature: '0xsig' })
  await settle()
  store.clearSession()
  login.resolve({ accessToken: 'stale-token' })
  await signing
  await settle()
  check('끊긴 세션이 유지된다', useAuthStore.getState().status, 'unauthenticated')
  check('버려진 토큰을 쓰지 않는다', globalThis.__authHarness.token, null)
}

// ── 4 ───────────────────────────────────────────────────────────
console.log('\n[4] 정상 경로')
{
  let reissues = 0
  let useAuthStore = await freshStore({
    restoreSession: async () => {
      reissues += 1
      return true
    },
    getMyProfile: async () => PROFILE,
  })
  // StrictMode 는 effect 를 두 번 태운다. 재발급이 두 번 나가면 서버가 이전
  // Refresh Token 을 폐기해 두 번째가 세션을 끊는다.
  await Promise.all([useAuthStore.getState().restore(), useAuthStore.getState().restore()])
  check('복구 성공 → authenticated', useAuthStore.getState().status, 'authenticated')
  check('프로필 주소를 쓴다', useAuthStore.getState().walletAddress, '0xaaa')
  check('이중 호출에도 재발급은 한 번', reissues, 1)

  useAuthStore = await freshStore({ restoreSession: async () => false })
  await useAuthStore.getState().restore()
  check('복구 실패 → unauthenticated', useAuthStore.getState().status, 'unauthenticated')

  useAuthStore = await freshStore({
    restoreSession: async () => true,
    getMyProfile: async () => {
      throw new Error('401')
    },
  })
  await useAuthStore.getState().restore()
  check('프로필 실패 → unauthenticated', useAuthStore.getState().status, 'unauthenticated')
  check('프로필 실패 시 토큰 폐기', globalThis.__authHarness.token, null)

  useAuthStore = await freshStore({
    restoreSession: async () => false,
    loginWithWallet: async () => ({ accessToken: 't' }),
    getMyProfile: async () => {
      throw new Error('401')
    },
  })
  let threw = false
  await useAuthStore
    .getState()
    .signIn({ walletAddress: '0xaaa', signature: '0xsig' })
    .catch(() => {
      threw = true
    })
  check('로그인 실패는 화면으로 던진다', threw, true)
  check('로그인 실패 → unauthenticated', useAuthStore.getState().status, 'unauthenticated')

  useAuthStore = await freshStore({
    restoreSession: async () => true,
    getMyProfile: async () => PROFILE,
    logout: async () => {
      throw new Error('500')
    },
  })
  await useAuthStore.getState().restore()
  await useAuthStore
    .getState()
    .signOut()
    .catch(() => {})
  check('로그아웃 요청이 실패해도 끊는다', useAuthStore.getState().status, 'unauthenticated')
  check('로그아웃 후 토큰 없음', globalThis.__authHarness.token, null)
}

await server.close()
console.log(`\n${passed} passed, ${failed} failed`)
process.exit(failed ? 1 : 0)
