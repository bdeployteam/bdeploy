package io.bdeploy.jersey.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.bdeploy.common.actions.Actions;
import io.bdeploy.common.audit.NullAuditor;
import io.bdeploy.jersey.ws.change.ObjectChangeBroadcaster;
import io.bdeploy.jersey.ws.change.msg.ObjectChangeDto;
import io.bdeploy.jersey.ws.change.msg.ObjectEvent;
import jakarta.ws.rs.WebApplicationException;

class ActionsTest {

    @Test
    void actions() throws Exception {
        var created = new AtomicReference<>(new CompletableFuture<>());
        var removed = new AtomicReference<>(new CompletableFuture<>());

        var bc = new ObjectChangeBroadcaster() {

            @Override
            public void sendBestMatching(List<ObjectChangeDto> changes) {
                // Not needed for testing
            }

            @Override
            public void send(ObjectChangeDto change) {
                if (change.event == ObjectEvent.CREATED) {
                    created.get().complete(true);
                } else {
                    removed.get().complete(true);
                }
            }
        };

        var svc = new ActionService(bc, new NullAuditor());

        assertEquals(0, svc.getRunningActions(null, null).size());

        var action = new Action(Actions.UPDATE, null, null, null);
        var exec = new ActionExecution("test");
        try (var handle = svc.start(action, exec)) {
            assertEquals(1, svc.getRunningActions(null, null).size());
            assertEquals(0, svc.getRunningActions("group", null).size());

            assertEquals(Boolean.TRUE, created.get().get(1, TimeUnit.SECONDS));
            created.set(new CompletableFuture<>());

            svc.add(new ActionBroadcastDto(action, exec));
            assertThrows(TimeoutException.class, () -> {
                created.get().get(1, TimeUnit.MILLISECONDS);
            });

            assertEquals(1, svc.getRunningActions(null, null).size());

            assertThrows(WebApplicationException.class, () -> {
                svc.start(action, new ActionExecution("test"));
            });
        }

        assertEquals(0, svc.getRunningActions(null, null).size());
        assertEquals(Boolean.TRUE, removed.get().get(1, TimeUnit.SECONDS));
    }
}
