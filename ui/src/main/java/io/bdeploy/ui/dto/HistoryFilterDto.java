package io.bdeploy.ui.dto;

import io.bdeploy.ui.api.impl.InstanceHistoryManager;

/**
 * Filter for the {@linkplain InstanceHistoryManager}
 */
public class HistoryFilterDto {

    /**
     * Include events from the PCU
     */
    public boolean showRuntimeEvents;

    /**
     * Include events when an instance was created
     */
    public boolean showCreateEvents;

    /**
     * Include events from the DCU
     */
    public boolean showDeploymentEvents;

    /**
     * The amount of results to return. Note that this is only a hint as always all events from a given instance version are
     * included in the result.
     * <p>
     * Thus when there are 3 instance versions A, B, C where A has 3 history entries, B has 100 history entries and C has 20. When
     * the total result is set to 20 then a total of 103 results are returned.
     * </p>
     */
    public int maxResults;

    /**
     * The instance version from where to start searching for events.
     */
    public String startTag;

    /**
     * The full-text filter to apply on the result.
     */
    public String filterText;

}
