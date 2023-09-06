package io.bdeploy.jersey.resources;

import java.util.List;

import io.bdeploy.jersey.actions.ActionBroadcastDto;
import io.bdeploy.jersey.actions.ActionService;
import jakarta.inject.Inject;

public class ActionResourceImpl implements ActionResource {

    @Inject
    private ActionService actions;

    @Override
    public List<ActionBroadcastDto> getActions(String group, String instance) {
        return actions.getRunningActions(group, instance);
    }

}
