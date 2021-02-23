package io.bdeploy.jersey.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.jersey.TestServer;
import io.bdeploy.jersey.ws.change.ObjectChangeWebSocket;
import io.bdeploy.jersey.ws.change.client.ObjectChangeClientWebSocket;
import io.bdeploy.jersey.ws.change.msg.ObjectChangeDto;
import io.bdeploy.jersey.ws.change.msg.ObjectEvent;
import io.bdeploy.jersey.ws.change.msg.ObjectScope;

public class ObjectChangeTest {

    @RegisterExtension
    TestServer ext = new TestServer();

    ObjectChangeWebSocket ocb;

    public ObjectChangeTest() {
        ocb = new ObjectChangeWebSocket(ext.getKeyStore());
        ext.registerWebsocketApplication(ObjectChangeWebSocket.OCWS_PATH, ocb);
    }

    @Test
    void testWebSocket(RemoteService remote) throws Exception {
        LongAdder receivedChanges = new LongAdder();
        CompletableFuture<ObjectChangeDto> withoutScope = new CompletableFuture<>();
        CompletableFuture<ObjectChangeDto> withScope = new CompletableFuture<>();

        AtomicReference<CompletableFuture<?>> barrier = new AtomicReference<>();
        ocb.addListener((r) -> {
            barrier.get().complete(null);
        });

        try (ObjectChangeClientWebSocket occws = JerseyClientFactory.get(remote).getObjectChangeWebSocket((change) -> {
            receivedChanges.increment();
            if (new ObjectScope("SCOPE").matches(change.scope)) {
                withScope.complete(change);
            } else {
                withoutScope.complete(change);
            }
        })) {
            barrier.set(new CompletableFuture<>());
            occws.subscribe("X", new ObjectScope("SCOPE"));
            barrier.get().get();

            // wrong scope
            ocb.send(new ObjectChangeDto("X", ObjectScope.EMPTY, ObjectEvent.CREATED, Collections.emptyMap()));

            // wrong type
            ocb.send(new ObjectChangeDto("Y", new ObjectScope("SCOPE"), ObjectEvent.CREATED, Collections.emptyMap()));

            // matches type and scope
            ocb.send(new ObjectChangeDto("X", new ObjectScope("SCOPE"), ObjectEvent.CREATED, Collections.emptyMap()));

            barrier.set(new CompletableFuture<>());
            occws.unsubscribe("X", new ObjectScope("SCOPE"));
            barrier.get().get();

            // no subscription anymore
            ocb.send(new ObjectChangeDto("X", new ObjectScope("SCOPE"), ObjectEvent.CREATED, Collections.emptyMap()));

            barrier.set(new CompletableFuture<>());
            occws.subscribe("X", ObjectScope.EMPTY);
            barrier.get().get();

            // test empty scope - the last test needs to be one which triggers the listener to allow asserts after this point.
            ocb.send(new ObjectChangeDto("X", ObjectScope.EMPTY, ObjectEvent.CREATED, Collections.emptyMap()));

            withScope.get();
            withoutScope.get();

            assertEquals(2, receivedChanges.sum());
        }
    }

}
