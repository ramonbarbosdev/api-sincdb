package com.api_sincdb.util;

import java.util.UUID;

public class TenantUtil {
    

    public static String generateTenantId() {
        return UUID.randomUUID().toString(); // ex: "a1b2c3d4-e5f6-7890-1234-56789abcdef0"
    }
}
