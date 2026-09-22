package com.aiblockclock.domain.auth;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.springframework.stereotype.Component;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class WalletSignatureValidator {
    private static final int SIGNATURE_BYTE_LENGTH = 65;

    public void check(WalletAuthMessage authMessage, String signature) {
        byte[] messageBytes = authMessage.message().getBytes(StandardCharsets.UTF_8);
        BigInteger publicKey;

        try {
            Sign.SignatureData signatureData = decode(signature);
            publicKey = Sign.signedPrefixedMessageToKey(messageBytes, signatureData);
        } catch (SignatureException | IllegalArgumentException exception) {
            throw new CoreException(ErrorType.WALLET_SIGNATURE_INVALID);
        }

        String recoveredAddress = "0x" + Keys.getAddress(publicKey);
        String expectedAddress = authMessage.walletAddress().toLowerCase(Locale.ROOT);

        if (!recoveredAddress.toLowerCase(Locale.ROOT).equals(expectedAddress)) {
            throw new CoreException(ErrorType.WALLET_SIGNATURE_INVALID);
        }
    }

    private Sign.SignatureData decode(String signature) throws SignatureException {
        if (signature == null) {
            throw new SignatureException("서명값이 없습니다.");
        }

        // Web3j의 hex 디코더가 잘못된 문자를 숫자로 해석하지 않도록 엄격하게 디코딩한다.
        byte[] signatureBytes = HexFormat.of().parseHex(Numeric.cleanHexPrefix(signature));

        if (signatureBytes.length != SIGNATURE_BYTE_LENGTH) {
            throw new SignatureException("지원하는 메시지 서명은 65바이트입니다.");
        }

        Sign.SignatureData signatureData = Sign.signatureDataFromHex(signature);
        BigInteger r = new BigInteger(1, signatureData.getR());
        BigInteger s = new BigInteger(1, signatureData.getS());
        BigInteger curveOrder = Sign.CURVE_PARAMS.getN();
        int v = Byte.toUnsignedInt(signatureData.getV()[0]);

        if ((v != 27 && v != 28) || r.signum() <= 0 || r.compareTo(curveOrder) >= 0
                || s.signum() <= 0 || s.compareTo(curveOrder) >= 0) {
            throw new SignatureException("서명 구성 값이 올바르지 않습니다.");
        }

        return signatureData;
    }
}
