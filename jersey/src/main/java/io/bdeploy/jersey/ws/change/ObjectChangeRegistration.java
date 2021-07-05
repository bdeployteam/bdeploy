package io.bdeploy.jersey.ws.change;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import io.bdeploy.jersey.ws.change.msg.ObjectScope;

/**
 * Manages registrations for certain object change types and related {@link ObjectScope}s.
 */
public class ObjectChangeRegistration {

    private final Map<String, List<ObjectScope>> registrations = new ConcurrentHashMap<>();
    private final List<Consumer<ObjectChangeRegistration>> listeners = new ArrayList<>();

    /**
     * Add a matching scope for the given type.
     */
    public void add(String type, ObjectScope scope) {
        registrations.computeIfAbsent(type, (t) -> new ArrayList<>()).add(scope);
        notifyListeners();
    }

    /**
     * Remove a matching scope for the given type.
     */
    public void remove(String type, ObjectScope scope) {
        List<ObjectScope> reg = registrations.get(type);
        if (reg != null) {
            reg.remove(scope);
        }
        notifyListeners();
    }

    /**
     * Determines whether the given {@link ObjectScope} is matched by any of the registered {@link ObjectScope}s.
     */
    public boolean matches(String type, ObjectScope scope) {
        List<ObjectScope> reg = registrations.get(type);
        if (reg == null) {
            return false;
        }

        // check if *any* scope matches.
        for (ObjectScope s : reg) {
            if (s.matches(scope)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calculates a match score, which determines "how much" the given scope matches.
     */
    public ObjectScope getBestScoring(String type, ObjectScope scope) {
        if (!matches(type, scope)) {
            return null;
        }

        ObjectScope result = null;
        int score = 0;
        for (ObjectScope s : registrations.get(type)) {
            int ss = s.score(scope);
            if (ss > score || (result != null && ss == score && s.length() < result.length())) {
                score = ss;
                result = s;
            }
        }
        return result;
    }

    /**
     * @param listener a listener to be notified on changes.
     */
    public void addListener(Consumer<ObjectChangeRegistration> listener) {
        this.listeners.add(listener);
    }

    private void notifyListeners() {
        for (Consumer<ObjectChangeRegistration> x : listeners) {
            x.accept(this);
        }
    }
}
