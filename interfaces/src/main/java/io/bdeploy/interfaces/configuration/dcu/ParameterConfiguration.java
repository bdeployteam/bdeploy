package io.bdeploy.interfaces.configuration.dcu;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes a single parameter as configured in a configuration UI.
 */
public class ParameterConfiguration {

    /**
     * The ID of the parameter, which can be used to reference it from other
     * parameters. This is only stored to allow lookup of parameters.
     */
    public String uid;

    /**
     * Whether this parameter is pinned. This information is used by the web UI to
     * more prominently present those parameters to the user.
     */
    public boolean pinned;

    /**
     * The actual value of the parameter as set in the UI. This field is potentially
     * redundant with an entry in the {@link #preRendered} list. The reason to store
     * it redundantly is solely lookup and expansion of other parameter's
     * references.
     */
    public LinkedValueConfiguration value;

    /**
     * The pre-rendered complete parameter as it should be appended to the command
     * line, but with variables still in place.
     */
    public List<String> preRendered = new ArrayList<>();

}
