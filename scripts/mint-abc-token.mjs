// abcToken 을 지정한 지갑에 발행하는 일회성 운영 도구다.
// 서버 기동 경로에 두지 않으려고 애플리케이션 밖에 스크립트로 둔다.
//
// 실행
//   SSAFY_TOKEN_CONTRACT_ADDRESS=0x... \
//   SSAFY_TOKEN_MINT_REQUESTOR=0x...   \
//   SSAFY_TOKEN_MINT_RECIPIENT=0x...   \
//   SSAFY_TOKEN_MINT_VALUE=1           \
//   SSAFY_TOKEN_MINT_DATA=...          \
//   SSAFY_TOKEN_MINT_SIGNATURE=0x...   \
//   node scripts/mint-abc-token.mjs
//
// 서명값은 민터 계정으로 직접 만들어 넣는다. 저장소에 두지 않는다.
//
// 수량은 ABC 단위로 넣는다. abcToken 은 decimals 18 이라 체인에는 ABC × 10^18 이 정수로 적힌다.
// 서명할 본문이 필요하면 서명 없이 SSAFY_TOKEN_MINT_PRINT_PAYLOAD=true 로 실행해 출력된 줄을 그대로 서명한다.

const TOKEN_NAME = 'ABC Payment Token';
const TOKEN_SYMBOL = 'abcToken';

const ADDRESS_PATTERN = /^0x[0-9a-fA-F]{40}$/;
const SIGNATURE_PATTERN = /^0x[0-9a-fA-F]{130}$/;
// 서버 장부가 소수 6자리까지 받는다. 그보다 잘게 발행하면 충전할 수 없는 자투리가 남는다
const ABC_AMOUNT_PATTERN = /^(\d+)(?:\.(\d{1,6}))?$/;
const TOKEN_DECIMALS = 18;

const baseUrl = (process.env.SSAFY_STO_BASE_URL ?? 'https://sto.ssafy.io').replace(/\/$/, '');
const contractAddress = requireAddress('SSAFY_TOKEN_CONTRACT_ADDRESS');
const requestor = requireAddress('SSAFY_TOKEN_MINT_REQUESTOR');
const recipient = requireAddress('SSAFY_TOKEN_MINT_RECIPIENT');
const abcAmount = requireValue('SSAFY_TOKEN_MINT_ABC');
const value = toRawAmount(abcAmount);
const data = requireValue('SSAFY_TOKEN_MINT_DATA');
const printPayloadOnly = process.env.SSAFY_TOKEN_MINT_PRINT_PAYLOAD === 'true';
const signature = printPayloadOnly ? null : requireSignature('SSAFY_TOKEN_MINT_SIGNATURE');

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

// 부동소수(Number)를 거치지 않고 문자열에서 바로 정수로 옮긴다. 10^18 단위는 Number 로 정확히 담기지 않는다
function toRawAmount(amount) {
    const matched = ABC_AMOUNT_PATTERN.exec(amount);

    if (!matched) {
        throw new Error('SSAFY_TOKEN_MINT_ABC 는 소수 6자리까지의 양수여야 합니다. 예: 10, 1.5');
    }

    const whole = BigInt(matched[1]);
    const fraction = BigInt((matched[2] ?? '').padEnd(TOKEN_DECIMALS, '0'));
    const raw = whole * 10n ** BigInt(TOKEN_DECIMALS) + fraction;

    if (raw <= 0n) {
        throw new Error('SSAFY_TOKEN_MINT_ABC 는 0보다 커야 합니다.');
    }

    return raw;
}

// JSON.stringify 는 BigInt 를 못 싣고, Number 로 바꾸면 끝자리가 뭉개지거나 1e+21 처럼 지수로 나간다.
// 수량만 정수 글자 그대로 끼워 넣는다
function requestBody(withSignature) {
    const fields = [
        `"requestor":${JSON.stringify(requestor)}`,
        `"to":${JSON.stringify(recipient)}`,
        `"value":${value.toString()}`,
        `"data":${JSON.stringify(data)}`,
    ];

    if (withSignature) {
        fields.push(`"signature":${JSON.stringify(signature)}`);
    }

    return `{${fields.join(',')}}`;
}

async function get(path) {
    const response = await fetch(`${baseUrl}${path}`, { headers: { Accept: 'application/json' } });

    if (!response.ok) {
        throw new Error(`GET ${path} 실패 (${response.status})`);
    }

    return (await response.text()).trim();
}

async function verifyAbcToken() {
    const name = await get(`/v1/tokens/${contractAddress}/name`);
    const symbol = await get(`/v1/tokens/${contractAddress}/symbol`);
    const granularity = BigInt(await get(`/v1/tokens/${contractAddress}/granularity`));

    if (name !== TOKEN_NAME || symbol !== TOKEN_SYMBOL) {
        throw new Error(`설정된 컨트랙트가 abcToken 이 아닙니다: ${name} / ${symbol}`);
    }

    if (value % granularity !== 0n) {
        throw new Error(`발행량은 최소 단위(${granularity})의 배수여야 합니다.`);
    }
}

async function verifyMintPermission() {
    const minter = await get(`/v1/tokens/${contractAddress}/roles/minter?address=${requestor}`);

    if (minter !== 'true') {
        throw new Error('요청 계정에 민터 권한이 없습니다.');
    }

    const issuable = await get(`/v1/tokens/${contractAddress}/status/issuance`);

    if (issuable !== 'true') {
        throw new Error('abcToken 발행이 막혀 있습니다.');
    }
}

async function readBalances() {
    return {
        balance: BigInt(await get(`/v1/tokens/${contractAddress}/balance?holderAddress=${recipient}`)),
        totalSupply: BigInt(await get(`/v1/tokens/${contractAddress}/total-supply`)),
    };
}

async function mint() {
    const response = await fetch(`${baseUrl}/v1/tokens/${contractAddress}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: requestBody(true),
    });

    if (!response.ok) {
        throw new Error(`발행 요청 실패 (${response.status}) — 체인 결과를 확인한 뒤 재시도하십시오.`);
    }

    const receipt = await response.json();

    if (receipt.status !== true || !receipt.transactionHash) {
        throw new Error(`발행 트랜잭션 실패: ${JSON.stringify(receipt)}`);
    }

    return receipt;
}

function verifyIncrease(target, before, after) {
    if (after !== before + value) {
        throw new Error(
            `발행은 끝났지만 ${target} 이 요청량만큼 늘지 않았습니다. `
            + `새로 발행하기 전에 트랜잭션을 확인하십시오. (${before} → ${after})`
        );
    }
}

await verifyAbcToken();

if (printPayloadOnly) {
    console.log(requestBody(false));
    process.exit(0);
}

await verifyMintPermission();

const before = await readBalances();

if (before.balance !== 0n) {
    console.warn(`수신자가 이미 ${before.balance} 개를 갖고 있습니다. 중복 발행이 아닌지 확인하십시오.`);
}

const receipt = await mint();
const after = await readBalances();

verifyIncrease('수신자 잔액', before.balance, after.balance);
verifyIncrease('총공급량', before.totalSupply, after.totalSupply);

console.log(
    `발행 완료 recipient=${recipient} abc=${abcAmount} value=${value} tx=${receipt.transactionHash} `
    + `balance=${after.balance} totalSupply=${after.totalSupply}`
);
