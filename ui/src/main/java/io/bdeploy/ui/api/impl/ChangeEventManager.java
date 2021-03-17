package io.bdeploy.ui.api.impl;

import java.util.Map;
import java.util.TreeMap;

import org.jvnet.hk2.annotations.Service;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.jersey.JerseyScopeService;
import io.bdeploy.jersey.ws.change.ObjectChangeBroadcaster;
import io.bdeploy.jersey.ws.change.msg.ObjectChangeDto;
import io.bdeploy.jersey.ws.change.msg.ObjectEvent;
import io.bdeploy.jersey.ws.change.msg.ObjectScope;
import io.bdeploy.ui.dto.ObjectChangeDetails;
import io.bdeploy.ui.dto.ObjectChangeType;
import jakarta.inject.Inject;

/**
 * Broadcasts events to attached applications (web app, other servers, ...)
 */
@Service
public class ChangeEventManager {

    @Inject
    private JerseyScopeService scope;

    @Inject
    private ObjectChangeBroadcaster bc;

    private Map<String, String> detailsFromKey(Manifest.Key key) {
        return Map.of(ObjectChangeDetails.KEY_NAME.name(), key.getName(), ObjectChangeDetails.KEY_TAG.name(), key.getTag());
    }

    public void create(ObjectChangeType type, Manifest.Key key) {
        bc.send(new ObjectChangeDto(type.name(), scope.getObjectScope(), ObjectEvent.CREATED, detailsFromKey(key)));
    }

    public void create(ObjectChangeType type, Manifest.Key key, ObjectScope scope) {
        bc.send(new ObjectChangeDto(type.name(), scope, ObjectEvent.CREATED, detailsFromKey(key)));
    }

    public void create(ObjectChangeType type, Map<ObjectChangeDetails, ?> details) {
        Map<String, String> merged = new TreeMap<>();
        details.forEach((dk, dv) -> merged.put(dk.name(), dv.toString()));
        bc.send(new ObjectChangeDto(type.name(), scope.getObjectScope(), ObjectEvent.CREATED, merged));
    }

    public void change(ObjectChangeType type, Manifest.Key key) {
        change(type, key, scope.getObjectScope());
    }

    public void change(ObjectChangeType type, Manifest.Key key, ObjectScope s) {
        bc.send(new ObjectChangeDto(type.name(), s, ObjectEvent.CHANGED, detailsFromKey(key)));
    }

    public void change(ObjectChangeType type, Manifest.Key key, Map<ObjectChangeDetails, ?> details) {
        change(type, key, scope.getObjectScope(), details);
    }

    public void change(ObjectChangeType type, Manifest.Key key, ObjectScope s, Map<ObjectChangeDetails, ?> details) {
        Map<String, String> merged = new TreeMap<>();
        merged.putAll(detailsFromKey(key));
        details.forEach((dk, dv) -> merged.put(dk.name(), dv.toString()));
        bc.send(new ObjectChangeDto(type.name(), s, ObjectEvent.CHANGED, merged));
    }

    public void remove(ObjectChangeType type, Manifest.Key key) {
        remove(type, key, scope.getObjectScope());
    }

    public void remove(ObjectChangeType type, Manifest.Key key, ObjectScope s) {
        bc.send(new ObjectChangeDto(type.name(), s, ObjectEvent.REMOVED, detailsFromKey(key)));
    }

}
