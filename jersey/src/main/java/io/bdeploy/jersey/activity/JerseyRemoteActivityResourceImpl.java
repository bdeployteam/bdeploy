package io.bdeploy.jersey.activity;

import java.util.Objects;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

/**
 * Resource allowing cancellation of activities on the server.
 */
@Singleton
@Path("/activities")
public class JerseyRemoteActivityResourceImpl {

    @Inject
    private JerseyBroadcastingActivityReporter reporter;

    @DELETE
    @Path("/{taskId}")
    public void cancelTask(@PathParam("taskId") String taskId) {
        JerseyRemoteActivity activity = reporter.getGlobalActivities().stream().filter(Objects::nonNull)
                .filter(a -> a.getUuid().equals(taskId)).findFirst().orElse(null);
        if (activity != null) {
            activity.requestCancel();
        }
    }

}
