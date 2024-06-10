package io.bdeploy.launcher;

import java.util.HashMap;
import java.util.Map;

import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;

public class LocalClientApplicationSettings {

    private final Map<String, StartScriptInfo> startScriptName2startDescriptor = new HashMap<>();

    /**
     * @param scriptName The {@link StartScriptInfo} to add to the internal {@link Map}
     * @param scriptInfo The {@link ClickAndStartDescriptor}
     * @param override Will use {@link Map#put(Object, Object) put} if <code>true</code>, otherwise
     *            {@link Map#putIfAbsent(Object, Object) putIfAbsent}
     */
    public void putStartScriptInfo(String scriptName, StartScriptInfo scriptInfo, boolean override) {
        if (override) {
            startScriptName2startDescriptor.put(scriptName, scriptInfo);
        } else {
            startScriptName2startDescriptor.putIfAbsent(scriptName, scriptInfo);
        }
    }

    /**
     * @param scriptName The name of the start script
     * @see Map#get(Object)
     */
    public StartScriptInfo getStartScriptInfo(String scriptName) {
        return startScriptName2startDescriptor.get(scriptName);
    }

    public static class StartScriptInfo {

        private final String fullScriptName;
        private final ClickAndStartDescriptor descriptor;

        public StartScriptInfo() {
            this(null, null);
        }

        public StartScriptInfo(String fullScriptName, ClickAndStartDescriptor descriptor) {
            this.fullScriptName = fullScriptName;
            this.descriptor = descriptor;
        }

        public String getFullScriptName() {
            return fullScriptName;
        }

        public ClickAndStartDescriptor getDescriptor() {
            return descriptor;
        }
    }
}
