package io.bdeploy.interfaces.manifest.history.runtime;

import java.util.HashMap;
import java.util.Map;

/**
 * The runtime history of all applications running on a minion.
 */
public class MinionRuntimeHistoryDto {

    private final Map<String, MinionRuntimeHistory> tag2History = new HashMap<>();

    /**
     * Adds a new entry for the given instance tag
     */
    public void add(String tag, MinionRuntimeHistory history) {
        tag2History.put(tag, history);
    }

    /**
     * Returns the history for the given tag
     */
    public MinionRuntimeHistory get(String tag) {
        return tag2History.get(tag);
    }

    /**
     * Returns all entries indexed by the instance tag.
     */
    public Map<String, MinionRuntimeHistory> getTag2History() {
        return tag2History;
    }

}
