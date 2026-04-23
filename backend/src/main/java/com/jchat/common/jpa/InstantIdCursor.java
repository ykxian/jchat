package com.jchat.common.jpa;

import com.jchat.common.api.ApiException;
import com.jchat.common.api.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import org.springframework.util.StringUtils;

public final class InstantIdCursor {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private InstantIdCursor() {
    }

    public static String encode(Instant instant, Long id) {
        String raw = instant.toString() + "|" + id;
        return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static CursorValue decodeNullable(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return null;
        }

        try {
            String decoded = new String(DECODER.decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 2);
            if (parts.length != 2) {
                throw invalidCursor();
            }
            return new CursorValue(Instant.parse(parts[0]), Long.parseLong(parts[1]));
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            throw invalidCursor();
        }
    }

    private static ApiException invalidCursor() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid cursor");
    }

    public record CursorValue(
            Instant instant,
            Long id
    ) {
    }
}
