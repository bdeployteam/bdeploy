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

    /**
     * @param taskId the task to cancel. Any parent task will be cancelled as well!
     */
    @DELETE
    @Path("/{taskId}")
    public void cancelTask(@PathParam("taskId") String taskId) {
        JerseyRemoteActivity activity = getActivityById(taskId);
        if (activity != null && !activity.isCancelRequested()) {
            activity.requestCancel();

            if (activity.getParentUuid() != null) {
                cancelTask(activity.getParentUuid());
            }
        }
    }

    private JerseyRemoteActivity getActivityById(String taskId) {
        return reporter.getGlobalActivities().stream().filter(Objects::nonNull).filter(a -> a.getUuid().equals(taskId))
                .findFirst().orElse(null);
    }

}
