package com.api_sincdb.config;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Parameters;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;

@Component
public class SshTunnelService {

    private static final int SSH_CONNECT_TIMEOUT_MS = 10000;

    private final Map<String, ManagedTunnel> tunnels = new ConcurrentHashMap<>();

    public ResolvedJdbcEndpoint resolve(
            String tunnelKey,
            boolean sshEnabled,
            String sshHost,
            String sshPort,
            String sshUser,
            String sshPassword,
            String remoteDbHost,
            String remoteDbPort) throws IOException {

        if (!sshEnabled) {
            return new ResolvedJdbcEndpoint(remoteDbHost, remoteDbPort, null);
        }

        validarSsh(sshHost, sshPort, sshUser, sshPassword);
        validarDestinoRemoto(remoteDbHost, remoteDbPort);

        int sshPortNumber = parsePort(sshPort, 22);
        int remotePortNumber = parsePort(remoteDbPort, 5432);
        String remoteHost = remoteDbHost.trim();

        ManagedTunnel tunnel = tunnels.compute(tunnelKey, (key, existing) -> {
            if (existing != null && existing.isOpen()) {
                return existing;
            }
            if (existing != null) {
                existing.closeQuietly();
            }
            try {
                return ManagedTunnel.open(
                        sshHost.trim(),
                        sshPortNumber,
                        sshUser.trim(),
                        sshPassword,
                        remoteHost,
                        remotePortNumber);
            } catch (IOException e) {
                throw new IllegalStateException("Falha ao abrir tunel SSH: " + e.getMessage(), e);
            }
        });

        return new ResolvedJdbcEndpoint(tunnel.getLocalHost(), String.valueOf(tunnel.getLocalPort()), tunnel);
    }

    public void closeTunnel(String tunnelKey) {
        ManagedTunnel tunnel = tunnels.remove(tunnelKey);
        if (tunnel != null) {
            tunnel.closeQuietly();
        }
    }

    public void closeAll() {
        for (ManagedTunnel tunnel : tunnels.values()) {
            tunnel.closeQuietly();
        }
        tunnels.clear();
    }

    private void validarSsh(String host, String port, String user, String password) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Host SSH nao informado.");
        }
        if (port == null || port.isBlank()) {
            throw new IllegalArgumentException("Porta SSH nao informada.");
        }
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("Usuario SSH nao informado.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Senha SSH nao informada.");
        }
    }

    private void validarDestinoRemoto(String host, String port) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Host do banco remoto nao informado.");
        }
        if (port == null || port.isBlank()) {
            throw new IllegalArgumentException("Porta do banco remoto nao informada.");
        }
    }

    private int parsePort(String port, int fallback) {
        try {
            return Integer.parseInt(port.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public record ResolvedJdbcEndpoint(String host, String port, ManagedTunnel tunnel) {
    }

    static final class ManagedTunnel {
        private final SSHClient sshClient;
        private final ServerSocket serverSocket;
        private final int localPort;
        private volatile boolean open = true;

        private ManagedTunnel(SSHClient sshClient, ServerSocket serverSocket, int localPort) {
            this.sshClient = sshClient;
            this.serverSocket = serverSocket;
            this.localPort = localPort;
        }

        static ManagedTunnel open(
                String sshHost,
                int sshPort,
                String sshUser,
                String sshPassword,
                String remoteDbHost,
                int remoteDbPort) throws IOException {

            SSHClient sshClient = new SSHClient();
            sshClient.addHostKeyVerifier(new PromiscuousVerifier());
            sshClient.setConnectTimeout(SSH_CONNECT_TIMEOUT_MS);
            sshClient.setTimeout(SSH_CONNECT_TIMEOUT_MS);
            sshClient.connect(sshHost, sshPort);
            sshClient.authPassword(sshUser, sshPassword);

            ServerSocket serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));

            int localPort = serverSocket.getLocalPort();
            Parameters params = new Parameters("127.0.0.1", localPort, remoteDbHost, remoteDbPort);
            var portForwarder = sshClient.newLocalPortForwarder(params, serverSocket);
            Thread listener = new Thread(() -> {
                try {
                    portForwarder.listen();
                } catch (IOException ignored) {
                    // Tunel encerrado.
                }
            }, "ssh-tunnel-" + localPort);
            listener.setDaemon(true);
            listener.start();

            return new ManagedTunnel(sshClient, serverSocket, localPort);
        }

        String getLocalHost() {
            return "127.0.0.1";
        }

        int getLocalPort() {
            return localPort;
        }

        boolean isOpen() {
            return open && sshClient.isConnected();
        }

        void closeQuietly() {
            open = false;
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            try {
                sshClient.disconnect();
            } catch (IOException ignored) {
            }
        }
    }
}
