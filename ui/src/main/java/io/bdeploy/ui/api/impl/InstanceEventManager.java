package io.bdeploy.ui.api.impl;

import javax.inject.Inject;
import javax.inject.Named;

import org.jvnet.hk2.annotations.Service;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.jersey.JerseyScopeService;
import io.bdeploy.jersey.ws.JerseyEventBroadcaster;
import io.bdeploy.ui.dto.InstanceUpdateEventDto;
import io.bdeploy.ui.dto.InstanceUpdateEventDto.InstanceUpdateEventType;

/**
 * Manager capable of broadcasting instance related events.
 */
@Service
public class InstanceEventManager {

    public static final String INSTANCE_BROADCASTER = "InstanceEventBroadcaster";

    @Inject
    private JerseyScopeService scope;

    @Inject
    @Named(INSTANCE_BROADCASTER)
    private JerseyEventBroadcaster bc;

    public void create(String instanceId, Manifest.Key key) {
        bc.send(new InstanceUpdateEventDto(key, InstanceUpdateEventType.CREATE), scope.getScope());
    }

    public void stateChanged(String instanceId, Manifest.Key key) {
        bc.send(new InstanceUpdateEventDto(key, InstanceUpdateEventType.STATE_CHANGE), scope.getScope());
    }

}
