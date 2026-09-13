package com.visitor.system.visitor;

import com.visitor.system.config.VisitorProperties;
import com.visitor.system.visitor.service.CryptoService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoServiceTests {

    @Test
    void shouldEncryptAndDecryptWithTwentyFourByteKey() {
        VisitorProperties properties = new VisitorProperties();
        properties.getSecurity().setAesKey("123456789012345678901234");
        CryptoService cryptoService = new CryptoService(properties);
        cryptoService.init();

        String cipherText = cryptoService.encrypt("110101199001011234");

        assertThat(cipherText).startsWith("gcm:");
        assertThat(cryptoService.decrypt(cipherText)).isEqualTo("110101199001011234");
    }

    @Test
    void shouldEncryptAndDecryptWithThirtyTwoByteKey() {
        VisitorProperties properties = new VisitorProperties();
        properties.getSecurity().setAesKey("12345678901234567890123456789012");
        CryptoService cryptoService = new CryptoService(properties);
        cryptoService.init();

        String cipherText = cryptoService.encrypt("652311986564518467");

        assertThat(cipherText).startsWith("gcm:");
        assertThat(cryptoService.decrypt(cipherText)).isEqualTo("652311986564518467");
    }
}
