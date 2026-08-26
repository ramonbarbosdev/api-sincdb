package com.api_sincdb.util;

public final class SyncCacheKeys {

    private SyncCacheKeys() {
    }

    public static String estrutura(String database, String esquema, String scope) {
        return database + "_estrutura:" + esquema + ":" + normalizeScope(esquema, scope);
    }

    public static String dados(String database, String esquema, String scope) {
        return database + "_dados:" + esquema + ":" + normalizeScope(esquema, scope);
    }

    private static String normalizeScope(String esquema, String scope) {
        if (scope == null || scope.isBlank()) {
            return "__schema__";
        }
        if (scope.equals(esquema)) {
            return "__schema__";
        }
        if (scope.contains(".")) {
            String tail = scope.substring(scope.lastIndexOf('.') + 1);
            if (tail.equals(esquema)) {
                return "__schema__";
            }
            return tail;
        }
        return scope;
    }
}
