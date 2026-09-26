// 에이전트 원본 기록 전용 토큰(등기부)을 SSAFY 토큰증권 API 로 한 번 배포하는 일회성 운영 도구다.
// 이 토큰은 발행하지 않는다. 문서 칸(ERC-1400 documents)에 에이전트 버전마다 원작자와 메타데이터 해시를 적는 게시판으로만 쓴다.
// 서버 기동 경로에 두지 않으려고 애플리케이션 밖에 스크립트로 둔다 (mint-abc-token.mjs 와 같은 이유).
//
// 1) 서명할 본문 출력
//   SSAFY_AGENT_REGISTRY_REQUESTOR=0x... SSAFY_AGENT_REGISTRY_PRINT_PAYLOAD=true node scripts/deploy-agent-registry.mjs
// 2) 출력된 한 줄을 등기부 지갑(MetaMask)으로 personal_sign 한다
// 3) 배포
//   SSAFY_AGENT_REGISTRY_REQUESTOR=0x... SSAFY_AGENT_REGISTRY_SIGNATURE=0x... node scripts/deploy-agent-registry.mjs
//
// requestor 가 토큰의 주인이 된다. 백엔드가 문서를 적을 때 이 지갑의 키로 서명하므로, 서버에 넣을 키의 지갑이어야 한다.
// 서명값은 저장소에 두지 않는다.

const TOKEN_NAME = 'ABC Agent Registry';
const TOKEN_SYMBOL = 'abcAgentReg';
const GRANULARITY = 1;

const ADDRESS_PATTERN = /^0x[0-9a-fA-F]{40}$/;
const SIGNATURE_PATTERN = /^0x[0-9a-fA-F]{130}$/;

const baseUrl = (process.env.SSAFY_STO_BASE_URL ?? 'https://sto.ssafy.io').replace(/\/$/, '');
const requestor = requireAddress('SSAFY_AGENT_REGISTRY_REQUESTOR');
const printPayloadOnly = process.env.SSAFY_AGENT_REGISTRY_PRINT_PAYLOAD === 'true';
const signature = printPayloadOnly ? null : requireSignature('SSAFY_AGENT_REGISTRY_SIGNATURE');

function requireValue(name) {
    const value = process.env[name];

    if (!value || !value.trim()) {
        throw new Error(`환경변수 ${name} 가 필요합니다.`);
    }

    return value.trim();
}

function requireAddress(name) {
    const value = requireValue(name);

    if (!ADDRESS_PATTERN.test(value)) {
        throw new Error(`${name} 는 0x 로 시작하는 40자리 16진수 주소여야 합니다.`);
    }

    return value;
}

function requireSignature(name) {
    const value = requireValue(name);

    if (!SIGNATURE_PATTERN.test(value)) {
        throw new Error(`${name} 는 0x 로 시작하는 130자리 16진수 서명이어야 합니다.`);
    }

    return value;
}

// 토큰증권 API 는 본문에서 signature 를 뺀 JSON 을 서명 대상으로 본다. 칸 순서는 API 명세(DeployTokenDto) 순서다.
// 발행 · 문서 기록과 같은 규칙이다. 컨트롤러는 주인 하나이고, 파티션은 쓰지 않는다
function requestBody(withSignature) {
    const body = {
        requestor,
        ...(withSignature ? { signature } : {}),
        name: TOKEN_NAME,
        symbol: TOKEN_SYMBOL,
        granularity: GRANULARITY,
        controllers: [requestor],
        partitions: [],
    };

    return JSON.stringify(body);
}

async function get(path) {
    const response = await fetch(`${baseUrl}${path}`, { headers: { Accept: 'application/json' } });

    if (!response.ok) {
        throw new Error(`GET ${path} 실패 (${response.status})`);
    }

    return response.json();
}

// 같은 주인이 같은 이름 · 심볼로 이미 배포했으면 멈춘다. 등기부가 둘이면 어느 쪽 기록을 믿을지 갈린다
async function refuseDuplicate() {
    const deployed = await get(`/v1/tokens/${requestor}`).catch(() => []);
    const existing = (Array.isArray(deployed) ? deployed : []).find((token) =>
        token?.name === TOKEN_NAME || token?.symbol === TOKEN_SYMBOL);

    if (existing) {
        throw new Error(`등기부가 이미 배포되어 있습니다: ${JSON.stringify(existing)}`);
    }
}

async function deploy() {
    const response = await fetch(`${baseUrl}/v1/tokens`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: requestBody(true),
    });
    const text = await response.text();

    if (!response.ok) {
        throw new Error(`배포 요청 실패 (${response.status}): ${text} — 체인 결과를 확인한 뒤 재시도하십시오.`);
    }

    const receipt = JSON.parse(text);

    if (receipt.status !== true || !receipt.contractAddress) {
        throw new Error(`배포 트랜잭션 실패: ${text}`);
    }

    return receipt;
}

async function verify(contractAddress) {
    const owner = await get(`/v1/tokens/${contractAddress}/owner`);
    const documents = await get(`/v1/tokens/${contractAddress}/documents`);

    if (String(owner.owner).toLowerCase() !== requestor.toLowerCase()) {
        throw new Error(`배포는 끝났지만 주인이 요청 지갑이 아닙니다: ${JSON.stringify(owner)}`);
    }

    return documents;
}

if (printPayloadOnly) {
    console.log(requestBody(false));
    process.exit(0);
}

await refuseDuplicate();

const receipt = await deploy();
const documents = await verify(receipt.contractAddress);

console.log(
    `배포 완료 contractAddress=${receipt.contractAddress} tx=${receipt.transactionHash} `
    + `block=${receipt.blockNumber} owner=${requestor} documents=${JSON.stringify(documents)}`
);
