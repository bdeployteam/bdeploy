package io.bdeploy.minion;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Timer;

import io.bdeploy.common.metrics.Metrics;
import io.bdeploy.common.metrics.Metrics.MetricGroup;
import io.bdeploy.common.security.RemoteService;

public class ConnectivityChecker {

    private static final Logger log = LoggerFactory.getLogger(ConnectivityChecker.class);
    private static final int DEFAULT_SSL_PORT = 443;

    private ConnectivityChecker() {
    }

    public static void checkOrThrow(RemoteService remote) {
        try {
            int port = remote.getUri().getPort();

            if (port == -1) {
                port = DEFAULT_SSL_PORT;
            }

            CompletableFuture<Boolean> accepted = new CompletableFuture<>();
            try (Timer.Context context = Metrics.getMetric(MetricGroup.CLI).timer("Connectivity Check").time();
                    ServerSocket s = new ServerSocket(port)) {
                new Thread(() -> {
                    try {
                        s.accept();
                        accepted.complete(true);
                    } catch (IOException e) {
                        accepted.completeExceptionally(e);
                    }
                }, "Connection-Check-Server").start();

                try (Socket c = new Socket(remote.getUri().getHost(), port)) {
                    if (!c.isConnected() || !accepted.get(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Connection not established after connection attempt");
                    }

                    log.info("Connection check performed successfully.");
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Cannot verify connectivity to " + remote.getUri() + ". Correct the hostname or port if necessary.", e);
        }
    }

}
