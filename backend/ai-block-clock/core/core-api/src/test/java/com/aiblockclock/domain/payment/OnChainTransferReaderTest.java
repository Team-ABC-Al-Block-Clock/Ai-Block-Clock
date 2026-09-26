package com.aiblockclock.domain.payment;

import com.aiblockclock.client.blockchain.BlockchainClientError;
import com.aiblockclock.client.blockchain.BlockchainClientException;
import com.aiblockclock.client.blockchain.ChainTokenTransfer;
import com.aiblockclock.client.blockchain.ChainTransaction;
import com.aiblockclock.client.blockchain.ChainTransactionStatus;
import com.aiblockclock.client.blockchain.SsafyChainClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import static com.aiblockclock.domain.payment.ChargeChainFixtures.CHAIN_ID;
import static com.aiblockclock.domain.payment.ChargeChainFixtures.CUSTODY;
import static com.aiblockclock.domain.payment.ChargeChainFixtures.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class OnChainTransferReaderTest {
    private static final String HASH = "0x" + "a".repeat(64);
    private static final String SENDER = "0x1111111111111111111111111111111111111111";
    private static final ChargeVerificationCriteria CRITERIA = new ChargeVerificationCriteria(
            CHAIN_ID, HASH, SENDER, DepositAmount.of(BigDecimal.TEN), CUSTODY, TOKEN, 5L);

    private final SsafyChainClient client = mock(SsafyChainClient.class);
    private final OnChainTransferReader reader = new OnChainTransferReader(client);

    @Test
    void 보관_지갑으로_간_abcToken을_10의_18제곱으로_나눠_토큰_개수로_읽는다() {
        given(client.readTransaction(HASH)).willReturn(mined(ChainTransactionStatus.SUCCEEDED,
                new ChainTokenTransfer(TOKEN, SENDER, CUSTODY, new BigInteger("10500000000000000000"))));

        OnChainTokenTransfer transfer = reader.read(CRITERIA);

        assertThat(transfer.status()).isEqualTo(OnChainTransactionStatus.SUCCESS);
        assertThat(transfer.chainId()).isEqualTo(CHAIN_ID);
        assertThat(transfer.senderWalletAddress()).isEqualTo(SENDER);
        assertThat(transfer.recipientAddress()).isEqualTo(CUSTODY);
        assertThat(transfer.tokenContractAddress()).isEqualTo(TOKEN);
        assertThat(transfer.amount()).isEqualByComparingTo("10.5");
        assertThat(transfer.confirmations()).isEqualTo(5L);
    }

    @Test
    void 소수_6자리를_넘는_자투리는_버리지_않고_그대로_읽어_금액_대조에서_걸리게_한다() {
        given(client.readTransaction(HASH)).willReturn(mined(ChainTransactionStatus.SUCCEEDED,
                new ChainTokenTransfer(TOKEN, SENDER, CUSTODY, new BigInteger("10000000000000000001"))));

        assertThat(reader.read(CRITERIA).amount()).isEqualByComparingTo("10.000000000000000001");
    }

    @Test
    void 보관_지갑이_아닌_곳으로_간_abcToken이면_받은_주소를_그대로_넘긴다() {
        given(client.readTransaction(HASH)).willReturn(mined(ChainTransactionStatus.SUCCEEDED,
                new ChainTokenTransfer(TOKEN, SENDER, "0x9999999999999999999999999999999999999999",
                        BigInteger.TEN.pow(19))));

        OnChainTokenTransfer transfer = reader.read(CRITERIA);

        assertThat(transfer.recipientAddress()).isEqualTo("0x9999999999999999999999999999999999999999");
        assertThat(transfer.amount()).isNull();
    }

    @Test
    void 다른_토큰을_보낸_거래면_그_토큰_주소를_넘긴다() {
        given(client.readTransaction(HASH)).willReturn(mined(ChainTransactionStatus.SUCCEEDED,
                new ChainTokenTransfer("0x4444444444444444444444444444444444444444", SENDER, CUSTODY,
                        BigInteger.TEN.pow(19))));

        assertThat(reader.read(CRITERIA).tokenContractAddress()).isEqualTo("0x4444444444444444444444444444444444444444");
    }

    @Test
    void 한_거래에_보관_지갑으로_가는_입금이_둘이면_금액을_비운다() {
        ChainTokenTransfer deposit = new ChainTokenTransfer(TOKEN, SENDER, CUSTODY, BigInteger.TEN.pow(19));
        given(client.readTransaction(HASH)).willReturn(mined(ChainTransactionStatus.SUCCEEDED, deposit, deposit));

        assertThat(reader.read(CRITERIA).amount()).isNull();
    }

    @Test
    void 블록에_적히기_전이면_확인_블록이_0이다() {
        given(client.readTransaction(HASH)).willReturn(
                new ChainTransaction(ChainTransactionStatus.PENDING, CHAIN_ID, null, null, List.of()));

        OnChainTokenTransfer transfer = reader.read(CRITERIA);

        assertThat(transfer.status()).isEqualTo(OnChainTransactionStatus.PENDING);
        assertThat(transfer.confirmations()).isZero();
    }

    @Test
    void 체인에_닿지_못하면_모른다고_돌려준다() {
        given(client.readTransaction(HASH))
                .willThrow(new BlockchainClientException(BlockchainClientError.REQUEST_FAILED, "timeout"));

        assertThat(reader.read(CRITERIA).isUnknown()).isTrue();
    }

    @Test
    void 노드_응답이_깨졌어도_모른다고_돌려준다() {
        given(client.readTransaction(HASH))
                .willThrow(new BlockchainClientException(BlockchainClientError.MALFORMED_RESPONSE, "broken"));

        assertThat(reader.read(CRITERIA).isUnknown()).isTrue();
    }

    @Test
    void 해시_형식이_틀린_거래는_체인에_없는_것으로_본다() {
        given(client.readTransaction(HASH))
                .willThrow(new BlockchainClientException(BlockchainClientError.INVALID_TRANSACTION_HASH, "bad"));

        assertThat(reader.read(CRITERIA).status()).isEqualTo(OnChainTransactionStatus.NOT_FOUND);
    }

    private static ChainTransaction mined(ChainTransactionStatus status, ChainTokenTransfer... transfers) {
        return new ChainTransaction(status, CHAIN_ID, BigInteger.valueOf(100), BigInteger.valueOf(104),
                List.of(transfers));
    }
}
