package io.bdeploy.jersey.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.glassfish.grizzly.websockets.DataFrame;
import org.glassfish.grizzly.websockets.WebSocket;
import org.glassfish.grizzly.websockets.WebSocketAdapter;
import org.glassfish.grizzly.websockets.WebSocketApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.ws.DefaultWebSocketListener;
import com.ning.http.client.ws.WebSocketUpgradeHandler;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.jersey.TestServer;

public class WebSocketTest {

    private static final Logger log = LoggerFactory.getLogger(WebSocketTest.class);

    @RegisterExtension
    TestServer ext = new TestServer();

    private final WebSocketTestApplication wsa;

    private final AtomicBoolean connected = new AtomicBoolean();
    private final AtomicBoolean received = new AtomicBoolean();
    private final AtomicBoolean sent = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    public WebSocketTest() {
        wsa = new WebSocketTestApplication();
        ext.registerWebsocketApplication("/test", wsa);
    }

    @Test
    void testWebSocket(RemoteService service) throws InterruptedException, ExecutionException {
        String testPayload = "This is a Test " + System.currentTimeMillis();
        try (AsyncHttpClient c = JerseyClientFactory.get(service).getWebSocketClient()) {
            AtomicReference<String> result = new AtomicReference<>();
            c.prepareGet(service.getWebSocketUri("/test").toString())
                    .execute(new WebSocketUpgradeHandler.Builder().addWebSocketListener(new DefaultWebSocketListener() {

                        @Override
                        public void onOpen(com.ning.http.client.ws.WebSocket websocket) {
                            log.info("Client open");

                            websocket.sendMessage(testPayload);
                            super.onOpen(websocket);
                        }

                        @Override
                        public void onError(Throwable t) {
                            log.info("Client error", t);
                        }

                        @Override
                        public void onClose(com.ning.http.client.ws.WebSocket websocket) {
                            log.info("Client close");
                            super.onClose(websocket);
                        }

                        @Override
                        public void onMessage(String message) {
                            log.info("Client message: " + message);

                            webSocket.close();
                            result.set(message);
                        }
                    }).build()).get();

            while (result.get() == null) {
                Thread.sleep(10);
            }

            assertEquals(testPayload, result.get());
            assertTrue(connected.get());
            assertTrue(received.get());
            assertTrue(sent.get());
            assertTrue(closed.get());
        }
    }

    public class WebSocketTestApplication extends WebSocketApplication {

        @Override
        public void onConnect(WebSocket socket) {
            connected.set(true);

            socket.add(new WebSocketAdapter() {

                @Override
                public void onMessage(WebSocket socket, String text) {
                    received.set(true);

                    socket.send(text);
                    sent.set(true);
                }

                @Override
                public void onClose(WebSocket socket, DataFrame frame) {
                    closed.set(true);
                }
            });

            super.onConnect(socket);
        }

    }

}
