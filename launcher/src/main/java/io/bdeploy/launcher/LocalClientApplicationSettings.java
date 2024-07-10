package io.bdeploy.launcher;

import java.util.HashMap;
import java.util.Map;

import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;

public class LocalClientApplicationSettings {

    private final Map<String, Boolean> startDescriptor2autostartEnabled = new HashMap<>();
    private final Map<String, ScriptInfo> startScriptName2startDescriptor = new HashMap<>();
    private final Map<String, ScriptInfo> fileAssocScriptName2startDescriptor = new HashMap<>();

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

    // Scripts

    /**
     * @param scriptName The name of the start script to remove
     * @see Map#remove(Object)
     */
    public void removeStartScriptInfo(String scriptName) {
        startScriptName2startDescriptor.remove(scriptName);
    }

    /**
     * @param scriptName The name of the start script
     * @param scriptInfo The {@link ScriptInfo} to add to the internal {@link Map}
     * @param override Will use {@link Map#put(Object, Object)} if <code>true</code>, otherwise
     *            {@link Map#putIfAbsent(Object, Object)}
     * @return <code>true</code> if the script is now considered active, otherwise <code>false</code>
     */
    public boolean putStartScriptInfo(String scriptName, ScriptInfo scriptInfo, boolean override) {
        if (override) {
            startScriptName2startDescriptor.put(scriptName, scriptInfo);
            return true;
        }
        startScriptName2startDescriptor.putIfAbsent(scriptName, scriptInfo);
        return startScriptName2startDescriptor.get(scriptName).descriptor.equals(scriptInfo.descriptor);
    }

    /**
     * @param scriptName The name of the start script
     * @see Map#get(Object)
     */
    public ScriptInfo getStartScriptInfo(String scriptName) {
        return startScriptName2startDescriptor.get(scriptName);
    }

    /**
     * @param scriptName The name of the file association script script to remove
     * @see Map#remove(Object)
     */
    public void removeFileAssocScriptInfo(String scriptName) {
        startScriptName2startDescriptor.remove(scriptName);
    }

    /**
     * @param scriptName The name of the file association script
     * @param scriptInfo The {@link ScriptInfo} to add to the internal {@link Map}
     * @param override Will use {@link Map#put(Object, Object)} if <code>true</code>, otherwise
     *            {@link Map#putIfAbsent(Object, Object)}
     * @return <code>true</code> if the script is now considered active, otherwise <code>false</code>
     */
    public boolean putFileAssocScriptInfo(String scriptName, ScriptInfo scriptInfo, boolean override) {
        if (override) {
            fileAssocScriptName2startDescriptor.put(scriptName, scriptInfo);
            return true;
        }
        fileAssocScriptName2startDescriptor.putIfAbsent(scriptName, scriptInfo);
        return fileAssocScriptName2startDescriptor.get(scriptName).descriptor.equals(scriptInfo.descriptor);
    }

    /**
     * @param scriptName The name of the file association script
     * @see Map#get(Object)
     */
    public ScriptInfo getFileAssocScriptInfo(String scriptName) {
        return fileAssocScriptName2startDescriptor.get(scriptName);
    }

    public static class ScriptInfo {

        private final String scriptName;
        private final ClickAndStartDescriptor descriptor;

        public ScriptInfo() {
            this(null, null);
        }

        public ScriptInfo(String scriptName, ClickAndStartDescriptor descriptor) {
            this.scriptName = scriptName;
            this.descriptor = descriptor;
        }

        public String getScriptName() {
            return scriptName;
        }

        public ClickAndStartDescriptor getDescriptor() {
            return descriptor;
        }
    }
}
