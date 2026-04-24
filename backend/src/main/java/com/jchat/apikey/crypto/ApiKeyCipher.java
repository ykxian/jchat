package com.jchat.apikey.crypto;

import com.jchat.common.api.ApiException;
import com.jchat.common.api.ErrorCode;
import com.jchat.config.AppProperties;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ApiKeyCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final AppProperties appProperties;
    private final SecureRandom secureRandom = new SecureRandom();
    private SecretKeySpec secretKey;

    public ApiKeyCipher(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @PostConstruct
    public void initialize() {
        if (!StringUtils.hasText(appProperties.getCrypto().getKey())) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "APP_CRYPTO_KEY is not configured");
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(appProperties.getCrypto().getKey().trim());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "APP_CRYPTO_KEY must be base64 encoded");
        }

        if (keyBytes.length != 32) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "APP_CRYPTO_KEY must decode to 32 bytes");
        }

        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        if (!StringUtils.hasText(plaintext)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "API key must not be blank");
        }

        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);
        byte[] encrypted = doCipher(Cipher.ENCRYPT_MODE, plaintext.getBytes(StandardCharsets.UTF_8), iv);
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        return Base64.getEncoder().encodeToString(combined);
    }

    public String decrypt(String encryptedValue) {
        if (!StringUtils.hasText(encryptedValue)) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Encrypted API key payload is blank");
        }

        byte[] combined;
        try {
            combined = Base64.getDecoder().decode(encryptedValue.trim());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Encrypted API key payload is invalid");
        }

        if (combined.length <= IV_LENGTH_BYTES) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Encrypted API key payload is too short");
        }

        byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH_BYTES);
        byte[] cipherBytes = Arrays.copyOfRange(combined, IV_LENGTH_BYTES, combined.length);
        byte[] decrypted = doCipher(Cipher.DECRYPT_MODE, cipherBytes, iv);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private byte[] doCipher(int mode, byte[] input, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(mode, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to process API key encryption");
        }
    }
}
