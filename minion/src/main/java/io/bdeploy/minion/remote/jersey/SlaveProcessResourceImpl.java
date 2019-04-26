package io.bdeploy.minion.remote.jersey;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;

import io.bdeploy.interfaces.configuration.pcu.InstanceNodeStatusDto;
import io.bdeploy.interfaces.remote.SlaveProcessResource;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.pcu.InstanceProcessController;
import io.bdeploy.pcu.MinionProcessController;

public class SlaveProcessResourceImpl implements SlaveProcessResource {

    @Inject
    private MinionRoot root;

    @Override
    public void start(String instanceId) {
        MinionProcessController processController = root.getProcessController();
        InstanceProcessController instanceController = processController.get(instanceId);
        if (instanceController == null) {
            throw new WebApplicationException("Instance with ID '" + instanceId + "' is unknown");
        }
        instanceController.start();
    }

    @Override
    public void start(String instanceId, String applicationId) {
        MinionProcessController processController = root.getProcessController();
        InstanceProcessController instanceController = processController.get(instanceId);
        if (instanceController == null) {
            throw new WebApplicationException("Instance with ID '" + instanceId + "' is unknown");
        }
        instanceController.start(applicationId);
    }

    @Override
    public void stop(String instanceId) {
        MinionProcessController processController = root.getProcessController();
        InstanceProcessController instanceController = processController.get(instanceId);
        if (instanceController == null) {
            throw new WebApplicationException("Instance with ID '" + instanceId + "' is unknown");
        }
        instanceController.stop();
    }

    @Override
    public void stop(String instanceId, String applicationId) {
        MinionProcessController processController = root.getProcessController();
        InstanceProcessController instanceController = processController.get(instanceId);
        if (instanceController == null) {
            throw new WebApplicationException("Instance with ID '" + instanceId + "' is unknown");
        }
        instanceController.stop(applicationId);
    }

    @Override
    public InstanceNodeStatusDto getStatus(String instanceId) {
        MinionProcessController processController = root.getProcessController();
        InstanceProcessController instanceController = processController.getOrCreate(instanceId);
        return instanceController.getStatus();
    }

}
