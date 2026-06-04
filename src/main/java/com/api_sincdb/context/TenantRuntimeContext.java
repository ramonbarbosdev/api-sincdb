package com.api_sincdb.context;

public class TenantRuntimeContext {

    private static final ThreadLocal<TenantRuntime> current = new ThreadLocal<>();

    public static void set(String idUsuario, String idEmpresa, String idTenant, String login) {
        current.set(new TenantRuntime(idUsuario, idEmpresa, idTenant, login));
    }

    public static TenantRuntime get() {
        return current.get();
    }

    public static String getIdUsuario() {
        TenantRuntime runtime = current.get();
        return runtime != null ? runtime.getIdUsuario() : null;
    }

    public static String getIdEmpresa() {
        TenantRuntime runtime = current.get();
        return runtime != null ? runtime.getIdEmpresa() : null;
    }

    public static String getIdTenant() {
        TenantRuntime runtime = current.get();
        return runtime != null ? runtime.getIdTenant() : null;
    }

    public static String getLogin() {
        TenantRuntime runtime = current.get();
        return runtime != null ? runtime.getLogin() : null;
    }

    public static void clear() {
        current.remove();
    }

    public static class TenantRuntime {
        private final String idUsuario;
        private final String idEmpresa;
        private final String idTenant;
        private final String login;

        public TenantRuntime(String idUsuario, String idEmpresa, String idTenant, String login) {
            this.idUsuario = idUsuario;
            this.idEmpresa = idEmpresa;
            this.idTenant = idTenant;
            this.login = login;
        }

        public String getIdUsuario() {
            return idUsuario;
        }

        public String getIdEmpresa() {
            return idEmpresa;
        }

        public String getIdTenant() {
            return idTenant;
        }

        public String getLogin() {
            return login;
        }
    }
}
