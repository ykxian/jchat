package com.jchat.auth.security;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordEncoderConfigTest {

    private static final Pattern BCRYPT_COST_12 = Pattern.compile("^\\$2[aby]\\$12\\$.*");

    @Test
    void createsBcryptEncoderWithCostTwelve() {
        PasswordEncoder encoder = new PasswordEncoderConfig().passwordEncoder();
        String encoded = encoder.encode("Passw0rd!");

        assertTrue(encoder.matches("Passw0rd!", encoded));
        assertTrue(BCRYPT_COST_12.matcher(encoded).matches());
    }
}
