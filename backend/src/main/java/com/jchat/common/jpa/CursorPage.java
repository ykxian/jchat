package com.jchat.common.jpa;

import java.util.List;

public record CursorPage<T>(
        List<T> items,
        String nextCursor
) {
}
