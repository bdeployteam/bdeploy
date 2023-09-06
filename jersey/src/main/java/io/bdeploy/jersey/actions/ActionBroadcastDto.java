package io.bdeploy.jersey.actions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.common.actions.ActionScope;

public class ActionBroadcastDto {

    public boolean exclusive;
    public String description;
    public ActionScope scope;

    public Action action;
    public ActionExecution execution;

    @JsonCreator
    public ActionBroadcastDto(@JsonProperty("action") Action action, @JsonProperty("execution") ActionExecution execution) {
        this.action = action;
        this.execution = execution;

        // this cannot be derived from the enum on the TypeScript side, so we extract it here.
        this.exclusive = action.getType().isExclusive();
        this.description = action.getType().getDescription();
        this.scope = action.getType().getScope();
    }
}
