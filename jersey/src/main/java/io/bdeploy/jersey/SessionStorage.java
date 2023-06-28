package io.bdeploy.jersey;

import java.util.Map;

public interface SessionStorage {

    /**
     * @return the currently persisted state.
     */
    public Map<String, String> load();

    /**
     * @param data the data to persist.
     */
    public void save(Map<String, String> data);

}
