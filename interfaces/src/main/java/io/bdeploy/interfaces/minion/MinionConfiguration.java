package io.bdeploy.interfaces.minion;

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Holds the list of known minions.
 */
public class MinionConfiguration {

    /**
     * The list of known minions indexed by their name
     */
    private final SortedMap<String, MinionDto> minions = new TreeMap<>();

    public MinionConfiguration() {
    }

    public MinionConfiguration(Map<String, MinionDto> config) {
        this.minions.putAll(config);
    }

    /**
     * Adds the given minion to the list of known minions
     *
     * @param minion the minion to add
     */
    public void addMinion(String minionName, MinionDto minion) {
        if (minionName == null) {
            throw new IllegalArgumentException("Minion does not have a name.");
        }
        if (minions.containsKey(minionName)) {
            throw new IllegalArgumentException("Minion with the same name already exists.");
        }
        minions.put(minionName, minion);
    }

    /**
     * Removes the minion with the given name from the list of known minions.
     *
     * @param minionName
     *            the name of the minion
     */
    public void removeMinion(String minionName) {
        minions.remove(minionName);
    }

    /**
     * Returns the details of the given minion
     *
     * @param minionName
     *            the name of the minion
     * @return the details
     */
    public MinionDto getMinion(String minionName) {
        MinionDto minionDto = minions.get(minionName);
        if (minionDto == null) {
            throw new IllegalArgumentException("Minion '" + minionName + "' is not registered.");
        }
        return minionDto;
    }

    /**
     * Returns a view of the minion mappings.
     */
    public Set<Entry<String, MinionDto>> entrySet() {
        return minions.entrySet();
    }

    /**
     * @return an unmodifiable view of the {@link Map} of all minions
     */
    public Map<String, MinionDto> minionMap() {
        return Collections.unmodifiableMap(minions);
    }

}
