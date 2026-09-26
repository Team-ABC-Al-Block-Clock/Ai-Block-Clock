package com.aiblockclock.client.blockchain;

import org.web3j.crypto.Credentials;

// 설정에서 읽어 낸 등기부 토큰 주소와, 그 토큰에 문서를 적을 때 서명하는 등기부 지갑이다.
record SsafyAgentRegistry(
        String contractAddress,
        Credentials signer
) {
    static SsafyAgentRegistry from(SsafyAgentRegistryProperties properties) {
        return new SsafyAgentRegistry(properties.contractAddress(), Credentials.create(properties.signerPrivateKey()));
    }
}
