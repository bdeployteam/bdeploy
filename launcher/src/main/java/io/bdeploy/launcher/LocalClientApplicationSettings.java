package io.bdeploy.launcher;

import java.util.HashMap;
import java.util.Map;

import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;

public class LocalClientApplicationSettings {

    private final Map<String, Boolean> startDescriptor2autostartEnabled = new HashMap<>();
    private final Map<String, StartScriptInfo> startScriptName2startDescriptor = new HashMap<>();

    // Autostart

    /**
     * @see Map#put(Object, Object)
     */
    public void putAutostartEnabled(ClickAndStartDescriptor descriptor, boolean enabled) {
        startDescriptor2autostartEnabled.put(stringifyClickAndStartDescriptor(descriptor), enabled);
    }

    /**
     * @see Map#get(Object)
     */
    public Boolean getAutostartEnabled(ClickAndStartDescriptor descriptor) {
        return startDescriptor2autostartEnabled.get(stringifyClickAndStartDescriptor(descriptor));
    }

    private static String stringifyClickAndStartDescriptor(ClickAndStartDescriptor descriptor) {
        return descriptor.groupId + ';' + descriptor.instanceId + ';' + descriptor.applicationId + ';' + descriptor.host.getUri();
    }

    // Start Script

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
