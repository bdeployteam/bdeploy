package io.bdeploy.interfaces.manifest.history.runtime;

import java.util.HashMap;
import java.util.Map;

/**
 * The runtime history of an entire instance
 */
public class MasterRuntimeHistoryDto {

    private final Map<String, MinionRuntimeHistoryDto> minion2History = new HashMap<>();
    private final Map<String, String> minion2Error = new HashMap<>();

    /**
     * Adds the given runtime history for the given minion
     */
    public synchronized void add(String minionName, MinionRuntimeHistoryDto history) {
        minion2History.put(minionName, history);
    }

    /**
     * Adds the given error message for the given minion
     */
    public synchronized void addError(String minionName, String error) {
        minion2Error.put(minionName, error);
    }

    /**
     * Returns the history
     */
    public Map<String, MinionRuntimeHistoryDto> getMinion2History() {
        return minion2History;
    }

    /**
     * Returns the errors
     */
    public Map<String, String> getMinion2Error() {
        return minion2Error;
    }

}
