import { resumeBroker } from './broker'
import { stopRuntime } from './runtime'

/**
 * 런타임 테스트가 남긴 것을 치운다. 테스트의 afterAll · finally 가 못 돈 경우(워커가 죽음)까지 받친다.
 * 브로커는 멈춰 있으면 되살리기만 한다 — 다른 테스트도 같이 쓰는 컨테이너다.
 */
export default async function runtimeTeardown() {
  try {
    resumeBroker()
  } finally {
    await stopRuntime()
  }
}
