package com.jchat.apikey.crypto;

import com.jchat.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ApiKeyCipherTest {

    @Test
    void encryptAndDecryptRoundTrip() {
        AppProperties appProperties = new AppProperties();
        ApiKeyCipher cipher = new ApiKeyCipher(appProperties);
        cipher.initialize();

        String encrypted = cipher.encrypt("sk-example-secret");

        assertNotEquals("sk-example-secret", encrypted);
        assertEquals("sk-example-secret", cipher.decrypt(encrypted));
    }
}
