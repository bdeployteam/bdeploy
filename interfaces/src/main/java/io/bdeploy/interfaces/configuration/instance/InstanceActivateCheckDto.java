package io.bdeploy.interfaces.configuration.instance;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class InstanceActivateCheckDto {

    /**
     * Maps node ID to a list of applications that are not or no longer allowed to run before activation of a given version.
     */
    public Map<String, List<String>> runningForbidden = new TreeMap<>();

}
