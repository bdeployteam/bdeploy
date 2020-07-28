package io.bdeploy.ui.api.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.view.ElementView;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ScanOperation;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.dcu.ParameterConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.history.InstanceManifestHistory.Action;
import io.bdeploy.interfaces.manifest.history.InstanceManifestHistoryRecord;
import io.bdeploy.ui.api.AuthService;
import io.bdeploy.ui.dto.HistoryEntryApplicationDto;
import io.bdeploy.ui.dto.HistoryEntryConfigFilesDto;
import io.bdeploy.ui.dto.HistoryEntryDto;
import io.bdeploy.ui.dto.HistoryEntryHttpEndpointDto;
import io.bdeploy.ui.dto.HistoryEntryNodeDto;
import io.bdeploy.ui.dto.HistoryEntryParametersDto;
import io.bdeploy.ui.dto.HistoryEntryVersionDto;

public class InstanceHistoryManager {

    private final Cache<String, List<HistoryEntryDto>> history = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(5)).maximumSize(10).build();

    @Inject
    private AuthService auth;

    private List<HistoryEntryDto> getCachedHistory(BHive hive, String instanceId) {
        try {
            return history.get(instanceId, () -> makeHistory(hive, instanceId));
        } catch (ExecutionException e) {
            throw new IllegalStateException("Cannot load history", e);
        }
    }

    private List<HistoryEntryDto> makeHistory(BHive hive, String instanceId) {
        List<HistoryEntryDto> instanceHistory = new ArrayList<>();

        String rootName = InstanceManifest.getRootName(instanceId);
        List<Manifest.Key> all = new ArrayList<>(hive.execute(new ManifestListOperation().setManifestName(rootName)));
        Collections.sort(all, (a, b) -> Integer.parseInt(b.getTag()) - Integer.parseInt(b.getTag()));

        for (Key manifestKey : all) {
            for (InstanceManifestHistoryRecord record : InstanceManifest.of(hive, manifestKey).getHistory(hive)
                    .getFullHistory()) {

                HistoryEntryDto entry = new HistoryEntryDto(record.timestamp, Integer.parseInt(manifestKey.getTag()),
                        record.action.name());
                UserInfo user = null;
                if (!record.user.isBlank()) {
                    if (record.user.charAt(0) == '[' && record.user.charAt(record.user.length() - 1) == ']') {
                        record.user = record.user.substring(1, record.user.length() - 1);
                    }
                    user = auth.getUser(record.user);
                }
                if (user != null) {
                    if (user.email != null && !user.email.isBlank()) {
                        entry.email = user.email;
                    }
                    if (user.fullName != null && !user.fullName.isBlank()) {
                        entry.user = user.fullName;
                    } else {
                        entry.user = record.user;
                    }
                } else {
                    entry.user = record.user;
                }

                entry.title = selectTitle(record.action, manifestKey.getTag());

                instanceHistory.add(entry);
            }
        }

        Collections.sort(instanceHistory, (a, b) -> Long.compare(a.timestamp, b.timestamp) * -1);
        return instanceHistory;
    }

    /**
     * Reads all Instance-History events and saves them.<br>
     * Computes all differences between two instance versions in a CREATE event. <br>
     * Returns a specified amount of events. <br>
     *
     * @param instanceId the uuid of the instance
     * @param amount how many events to be loaded
     * @return A list of history entries
     */
    public List<HistoryEntryDto> getInstanceHistory(BHive hive, String instanceId, int amount) {
        List<HistoryEntryDto> instanceHistory = makeHistory(hive, instanceId);
        history.put(instanceId, instanceHistory);
        List<HistoryEntryDto> returnList;

        if (amount < instanceHistory.size()) {
            returnList = instanceHistory.subList(0, amount);

            for (HistoryEntryDto entry : returnList) {
                if (entry.type == "CREATE" && entry.version > 1) {
                    entry.content = versionDifferences(hive,
                            InstanceManifest.load(hive, instanceId, String.valueOf(entry.version - 1)),
                            InstanceManifest.load(hive, instanceId, String.valueOf(entry.version)));
                }
            }

        } else {
            returnList = instanceHistory.subList(0, instanceHistory.size());

            for (HistoryEntryDto entry : returnList) {
                if (entry.type == "CREATE" && entry.version > 1) {
                    entry.content = versionDifferences(hive,
                            InstanceManifest.load(hive, instanceId, String.valueOf(entry.version - 1)),
                            InstanceManifest.load(hive, instanceId, String.valueOf(entry.version)));
                }
            }
        }

        return returnList;

    }

    private String selectTitle(Action action, String tag) {
        switch (action) {
            case CREATE:
                return "Version " + tag + ": Creation";
            case INSTALL:
                return "Version " + tag + ": Installation";
            case UNINSTALL:
                return "Version " + tag + ": Uninstallation";
            case ACTIVATE:
                return "Version " + tag + ": Activation";
            case DEACTIVATE:
                return "Version " + tag + ": Deactivation";
            default:
                return "";
        }
    }

    /**
     * Loads a specified amount of Events and returns them.<br>
     * Computes all differences between two instance versions in a CREATE event. <br>
     *
     * @param instanceId the instance to load the history of
     * @param amount how many events to load
     * @param offset load events starting from offset
     * @return a List of Events
     */
    public List<HistoryEntryDto> getMoreInstanceHistory(BHive hive, String instanceId, int amount, int offset) {

        List<HistoryEntryDto> returnList;
        List<HistoryEntryDto> cachedHistory = getCachedHistory(hive, instanceId);

        if (offset < cachedHistory.size()) {
            if (offset + amount < cachedHistory.size()) {
                returnList = cachedHistory.subList(offset, offset + amount);

                for (HistoryEntryDto entry : returnList) {
                    if (entry.type == "CREATE" && entry.version != 1) {
                        entry.content = versionDifferences(hive,
                                InstanceManifest.load(hive, instanceId, String.valueOf(entry.version - 1)),
                                InstanceManifest.load(hive, instanceId, String.valueOf(entry.version)));
                    }
                }

            } else {
                returnList = cachedHistory.subList(offset, cachedHistory.size());

                for (HistoryEntryDto entry : returnList) {
                    if (entry.type == "CREATE" && entry.version != 1) {
                        entry.content = versionDifferences(hive,
                                InstanceManifest.load(hive, instanceId, String.valueOf(entry.version - 1)),
                                InstanceManifest.load(hive, instanceId, String.valueOf(entry.version)));
                    }
                }
            }
        } else {
            returnList = new ArrayList<>();
        }

        return returnList;
    }

    /**
     * Computes the differences between two given instance versions.
     *
     * @param instanceId the instance to compare versions of
     * @param versionA the version tag of the earlier version
     * @param versionB the version tag of the later version
     * @return a string of the computed differences
     */
    public HistoryEntryVersionDto compareVersions(BHive hive, String instanceId, int versionA, int versionB) {
        InstanceManifest a = InstanceManifest.load(hive, instanceId, String.valueOf(versionA));
        InstanceManifest b = InstanceManifest.load(hive, instanceId, String.valueOf(versionB));
        return versionDifferences(hive, a, b);
    }

    private HistoryEntryVersionDto versionDifferences(BHive hive, InstanceManifest oldManifest, InstanceManifest newManifest) {
        HistoryEntryVersionDto content = new HistoryEntryVersionDto();

        InstanceConfiguration oldConfig = oldManifest.getConfiguration();
        InstanceConfiguration newConfig = newManifest.getConfiguration();

        // test for differences in configuration properties
        if (oldConfig.autoStart != newConfig.autoStart) {
            content.properties.put("Auto-start",
                    new String[] { String.valueOf(oldConfig.autoStart), String.valueOf(newConfig.autoStart) });
        }
        if (oldConfig.autoUninstall != newConfig.autoUninstall) {
            content.properties.put("Auto-uninstall",
                    new String[] { String.valueOf(oldConfig.autoUninstall), String.valueOf(newConfig.autoUninstall) });
        }
        if (oldConfig.description != null && !oldConfig.description.equals(newConfig.description)) {
            content.properties.put("Description", new String[] { oldConfig.description, newConfig.description });
        }
        if (oldConfig.name != null && !oldConfig.name.equals(newConfig.name)) {
            content.properties.put("Name", new String[] { oldConfig.name, newConfig.name });
        }
        if (oldConfig.purpose != null && !oldConfig.purpose.toString().equals(newConfig.purpose.toString())) {
            content.properties.put("Purpose", new String[] { oldConfig.purpose.name(), newConfig.purpose.name() });
        }

        // iterate through nodes
        SortedMap<String, Key> oldNodes = new TreeMap<>(oldManifest.getInstanceNodeManifests());
        SortedMap<String, Key> newNodes = new TreeMap<>(newManifest.getInstanceNodeManifests());

        List<String> oldNodeNames = new ArrayList<>(oldNodes.keySet());
        Collections.sort(oldNodeNames);
        List<String> newNodeNames = new ArrayList<>(newNodes.keySet());
        Collections.sort(newNodeNames);

        boolean finished = false;
        int comparison = 0;

        for (int i = 0; i <= newNodes.size() && !finished; i++) {

            while (true) {
                if (i >= newNodes.size()) {
                    for (int j = i; j < oldNodes.size(); j++) {
                        content.nodes.put(oldNodeNames.get(j),
                                deletedNode(hive, oldNodeNames.get(j), oldNodes.get(oldNodeNames.get(j))));
                    }
                    finished = true;
                    break;
                }
                if (i >= oldNodes.size()) {
                    for (int j = i; j < newNodes.size(); j++) {
                        content.nodes.put(newNodeNames.get(j),
                                addedNode(hive, newNodeNames.get(j), newNodes.get(newNodeNames.get(j))));
                    }
                    finished = true;
                    break;
                }

                comparison = newNodeNames.get(i).compareTo(oldNodeNames.get(i));

                if (comparison == 0) {

                    //this node stayed, so look for changes
                    HistoryEntryNodeDto ret = nodeDifferences(hive, oldNodes.get(oldNodeNames.get(i)),
                            newNodes.get(newNodeNames.get(i)));
                    if (ret != null) {
                        content.nodes.put(oldNodeNames.get(i), ret);
                    }
                    break;
                }
                if (comparison > 0) {

                    content.nodes.put(oldNodeNames.get(i),
                            deletedNode(hive, oldNodeNames.get(i), oldNodes.get(oldNodeNames.get(i))));
                    oldNodes.remove(oldNodeNames.get(i));
                    oldNodeNames.remove(i);
                }
                if (comparison < 0) {
                    content.nodes.put(oldNodeNames.get(i),
                            addedNode(hive, newNodeNames.get(i), newNodes.get(newNodeNames.get(i))));
                    newNodes.remove(newNodeNames.get(i));
                    newNodeNames.remove(i);
                }
            }
        }

        finished = false;
        HistoryEntryConfigFilesDto configFiles = new HistoryEntryConfigFilesDto();

        ObjectId oldConfigTree = oldConfig.configTree;
        ObjectId newConfigTree = newConfig.configTree;

        if (oldConfigTree == null) {
            if (newConfigTree != null) {
                for (String name : hive.execute(new ScanOperation().setTree(newConfigTree)).getChildren().keySet()) {
                    // added file
                    configFiles.added.add(name);
                }
            }
        } else if (newConfigTree == null) {
            for (String name : hive.execute(new ScanOperation().setTree(oldConfigTree)).getChildren().keySet()) {
                // deleted file
                configFiles.deleted.add(name);
            }
        } else {
            Map<String, ElementView> oldConfigFiles = new HashMap<>(
                    hive.execute(new ScanOperation().setTree(oldConfigTree)).getChildren());
            Map<String, ElementView> newConfigFiles = new HashMap<>(
                    hive.execute(new ScanOperation().setTree(newConfigTree)).getChildren());

            List<String> oldConfigFileNames = new ArrayList<>(oldConfigFiles.keySet());
            Collections.sort(oldConfigFileNames);
            List<String> newConfigFileNames = new ArrayList<>(newConfigFiles.keySet());
            Collections.sort(newConfigFileNames);

            for (int i = 0; i <= newConfigFiles.size() && !finished; i++) {
                while (true) {
                    if (i >= newConfigFiles.size()) {
                        for (int j = i; j < oldConfigFiles.size(); j++) {
                            // deleted file
                            configFiles.deleted.add(oldConfigFileNames.get(j));
                        }
                        finished = true;
                        break;
                    }
                    if (i >= oldConfigFiles.size()) {
                        for (int j = i; j < newConfigFiles.size(); j++) {
                            // added file
                            configFiles.added.add(newConfigFileNames.get(j));
                        }
                        finished = true;
                        break;
                    }
                    comparison = newConfigFileNames.get(i).compareTo(oldConfigFileNames.get(i));

                    if (comparison == 0) {
                        if (!oldConfigFiles.get(oldConfigFileNames.get(i)).getElementId().getId()
                                .equals(newConfigFiles.get(newConfigFileNames.get(i)).getElementId().getId())) {
                            configFiles.changed.add(oldConfigFileNames.get(i));
                        }
                        break;
                    }
                    if (comparison > 0) {
                        // deleted file
                        configFiles.deleted.add(oldConfigFileNames.get(i));
                        oldConfigFiles.remove(oldConfigFileNames.get(i));
                        oldConfigFileNames.remove(i);
                    } else if (comparison < 0) {
                        // added file
                        configFiles.deleted.add(newConfigFileNames.get(i));
                        newConfigFiles.remove(newConfigFileNames.get(i));
                        newConfigFileNames.remove(i);
                    }
                }
            }
        }
        if (configFiles.added.isEmpty() && configFiles.deleted.isEmpty() && configFiles.changed.isEmpty()) {
            content.configFiles = null;
        } else {
            content.configFiles = configFiles;
        }

        return content;
    }

    // a node gets added when any applications get added, so print all applications of the new version
    private HistoryEntryNodeDto addedNode(BHive hive, String NodeName, Key newKey) {
        List<ApplicationConfiguration> apps = InstanceNodeManifest.of(hive, newKey).getConfiguration().applications;

        HistoryEntryNodeDto node = new HistoryEntryNodeDto();

        for (ApplicationConfiguration app : apps) {
            node.added.add(app.name);
        }

        return node;
    }

    // a node gets deleted when all applications get deleted, so print all applications of the old version
    private HistoryEntryNodeDto deletedNode(BHive hive, String NodeName, Key oldKey) {
        List<ApplicationConfiguration> apps = InstanceNodeManifest.of(hive, oldKey).getConfiguration().applications;

        HistoryEntryNodeDto node = new HistoryEntryNodeDto();

        for (ApplicationConfiguration app : apps) {
            node.deleted.add(app.name);
        }

        return node;
    }

    private HistoryEntryNodeDto nodeDifferences(BHive hive, Key oldKey, Key newKey) {
        HistoryEntryNodeDto content = new HistoryEntryNodeDto();

        InstanceNodeConfiguration oldNodeConfig = InstanceNodeManifest.of(hive, oldKey).getConfiguration();
        InstanceNodeConfiguration newNodeConfig = InstanceNodeManifest.of(hive, newKey).getConfiguration();

        // iterate through applications
        List<ApplicationConfiguration> oldApplications = new ArrayList<>(oldNodeConfig.applications);
        Collections.sort(oldApplications, (a, b) -> a.uid.compareTo(b.uid));

        List<ApplicationConfiguration> newApplications = new ArrayList<>(newNodeConfig.applications);
        Collections.sort(newApplications, (a, b) -> a.uid.compareTo(b.uid));

        boolean finished = false;
        int comparison = 0;

        for (int i = 0; i <= newApplications.size() && !finished; i++) {
            while (true) {
                if (i >= newApplications.size()) {
                    for (int j = i; j < oldApplications.size(); j++) {
                        content.deleted.add(oldApplications.get(j).name);
                    }

                    finished = true;
                    break;
                }
                if (i >= oldApplications.size()) {
                    for (int j = i; j < newApplications.size(); j++) {
                        content.added.add(newApplications.get(j).name);
                    }
                    finished = true;
                    break;
                }
                comparison = newApplications.get(i).uid.compareTo(oldApplications.get(i).uid);
                if (comparison == 0) {
                    // this application stayed, so look for changes
                    HistoryEntryApplicationDto ret = applicationDifferences(oldApplications.get(i), newApplications.get(i));
                    if (ret != null) {
                        content.changed.put(oldApplications.get(i).name, ret);
                    }

                    break;
                }
                if (comparison > 0) {
                    content.deleted.add(oldApplications.get(i).name);
                    oldApplications.remove(i);
                }
                if (comparison < 0) {
                    content.added.add(newApplications.get(i).name);
                    newApplications.remove(i);
                }
            }
        }
        if (content.added.isEmpty() && content.deleted.isEmpty() && content.changed.isEmpty()) {
            return null;
        }
        return content;
    }

    private HistoryEntryApplicationDto applicationDifferences(ApplicationConfiguration oldConfig,
            ApplicationConfiguration newConfig) {
        HistoryEntryApplicationDto content = new HistoryEntryApplicationDto();

        // test for differences in config properties

        if (oldConfig.name != null && !oldConfig.name.equals(newConfig.name)) {
            content.properties.put("Name", new String[] { oldConfig.name, newConfig.name });
        }
        if (oldConfig.start.executable != null && !oldConfig.start.executable.equals(newConfig.start.executable)) {
            content.properties.put("Executable path", new String[] { oldConfig.start.executable, newConfig.start.executable });
        }

        if (oldConfig.processControl.attachStdin != newConfig.processControl.attachStdin) {
            content.properties.put("Attach to stdin", new String[] { String.valueOf(oldConfig.processControl.attachStdin),
                    String.valueOf(newConfig.processControl.attachStdin) });
        }
        if (oldConfig.processControl.keepAlive != newConfig.processControl.keepAlive) {
            content.properties.put("Keep alive", new String[] { String.valueOf(oldConfig.processControl.keepAlive),
                    String.valueOf(newConfig.processControl.keepAlive) });
        }
        if (oldConfig.processControl.gracePeriod != newConfig.processControl.gracePeriod) {
            content.properties.put("Grace period", new String[] { String.valueOf(oldConfig.processControl.gracePeriod),
                    String.valueOf(newConfig.processControl.gracePeriod) });
        }
        if (oldConfig.processControl.noOfRetries != newConfig.processControl.noOfRetries) {
            content.properties.put("Number of retries", new String[] { String.valueOf(oldConfig.processControl.noOfRetries),
                    String.valueOf(newConfig.processControl.noOfRetries) });
        }

        if (oldConfig.processControl.startType != null
                && !oldConfig.processControl.startType.equals(newConfig.processControl.startType)) {
            content.properties.put("Start type",
                    new String[] { oldConfig.processControl.startType.name(), newConfig.processControl.startType.name() });
        }

        HistoryEntryParametersDto parameters = new HistoryEntryParametersDto();

        List<ParameterConfiguration> oldParameters = oldConfig.start.parameters;
        List<ParameterConfiguration> newParameters = newConfig.start.parameters;

        Collections.sort(oldParameters, (a, b) -> a.uid.compareTo(b.uid));
        Collections.sort(newParameters, (a, b) -> a.uid.compareTo(b.uid));

        boolean finished = false;
        int comparison = 0;

        for (int i = 0; i <= newParameters.size() && !finished; i++) {
            while (true) {
                if (i >= newParameters.size()) {
                    for (int j = i; j < oldParameters.size(); j++) {
                        parameters.deleted.add(new String[] { oldParameters.get(j).uid, oldParameters.get(j).value });
                    }

                    finished = true;
                    break;
                }
                if (i >= oldParameters.size()) {
                    for (int j = i; j < newParameters.size(); j++) {
                        parameters.added.add(new String[] { newParameters.get(j).uid, newParameters.get(j).value });
                    }
                    finished = true;
                    break;
                }
                comparison = newParameters.get(i).uid.compareTo(oldParameters.get(i).uid);
                if (comparison == 0) {

                    if (!oldParameters.get(i).value.equals(newParameters.get(i).value)) {
                        parameters.changed.put(oldParameters.get(i).uid,
                                new String[] { oldParameters.get(i).value, newParameters.get(i).value });
                    }
                    break;
                }
                if (comparison > 0) {
                    parameters.deleted.add(new String[] { oldParameters.get(i).uid, oldParameters.get(i).value });
                    oldParameters.remove(i);
                }
                if (comparison < 0) {
                    parameters.added.add(new String[] { newParameters.get(i).uid, newParameters.get(i).value });
                    newParameters.remove(i);
                }
            }
        }
        if (!parameters.added.isEmpty() || !parameters.changed.isEmpty() || !parameters.deleted.isEmpty()) {
            content.parameters = parameters;
        } else {
            content.parameters = null;
        }

        // iterate trough http endpoints
        List<HttpEndpoint> oldEndpoints = oldConfig.endpoints.http;
        List<HttpEndpoint> newEndpoints = newConfig.endpoints.http;

        Collections.sort(oldEndpoints, (a, b) -> a.id.compareTo(b.id));
        Collections.sort(newEndpoints, (a, b) -> a.id.compareTo(b.id));

        finished = false;
        comparison = 0;

        for (int i = 0; i <= newEndpoints.size() && !finished; i++) {
            while (true) {
                if (i >= newEndpoints.size()) {
                    for (int j = i; j < oldEndpoints.size(); j++) {
                        content.deletedEndpoints.add(oldEndpoints.get(j).path + ":" + oldEndpoints.get(j).port);
                    }

                    finished = true;
                    break;
                }
                if (i >= oldEndpoints.size()) {
                    for (int j = i; j < newEndpoints.size(); j++) {
                        content.addedEndpoints.add(newEndpoints.get(j).path + ":" + newEndpoints.get(j).port);
                    }
                    finished = true;
                    break;
                }
                comparison = newEndpoints.get(i).id.compareTo(oldEndpoints.get(i).id);

                if (comparison == 0) {
                    HistoryEntryHttpEndpointDto ret = endpointsDifferences(oldEndpoints.get(i), newEndpoints.get(i));
                    if (!ret.properties.isEmpty()) {
                        content.endpoints.put(oldEndpoints.get(i).path + ":" + oldEndpoints.get(i).port, ret);
                    }
                    break;
                }
                if (comparison > 0) {
                    content.deletedEndpoints.add(oldEndpoints.get(i).path + ":" + oldEndpoints.get(i).port);
                    oldEndpoints.remove(i);
                }
                if (comparison < 0) {
                    content.addedEndpoints.add(newEndpoints.get(i).path + ":" + newEndpoints.get(i).port);
                    newEndpoints.remove(i);
                }
            }
        }
        if (content.addedEndpoints.isEmpty() && content.deletedEndpoints.isEmpty() && content.endpoints.isEmpty()
                && content.parameters == null && content.processControlProperties.isEmpty() && content.properties.isEmpty()) {
            return null;
        }
        return content;

    }

    private HistoryEntryHttpEndpointDto endpointsDifferences(HttpEndpoint oldEndpoint, HttpEndpoint newEndpoint) {

        HistoryEntryHttpEndpointDto content = new HistoryEntryHttpEndpointDto();

        // test for differences in http endpoint properties

        if (oldEndpoint.authPass != null && !oldEndpoint.authPass.equals(newEndpoint.authPass)) {
            content.properties.put("Authentication password", null);
        }
        if (oldEndpoint.authUser != null && !oldEndpoint.authUser.equals(newEndpoint.authUser)) {
            content.properties.put("User", new String[] { oldEndpoint.authUser, newEndpoint.authUser });
        }
        if (oldEndpoint.authType != newEndpoint.authType) {
            content.properties.put("Authentication type",
                    new String[] { oldEndpoint.authType.name(), newEndpoint.authType.name() });
        }
        if (oldEndpoint.path != null && !oldEndpoint.path.equals(newEndpoint.path)) {
            content.properties.put("Path", new String[] { oldEndpoint.authUser, newEndpoint.authUser });
        }
        if (oldEndpoint.port != null && !oldEndpoint.port.equals(newEndpoint.port)) {
            content.properties.put("Port", new String[] { oldEndpoint.authUser, newEndpoint.authUser });
        }
        if (oldEndpoint.trustStore != null && !oldEndpoint.trustStore.equals(newEndpoint.trustStore)) {
            content.properties.put("Trust-store path", new String[] { oldEndpoint.authUser, newEndpoint.authUser });
        }
        if (oldEndpoint.trustStorePass != null && !oldEndpoint.trustStorePass.equals(newEndpoint.trustStorePass)) {
            content.properties.put("Trust-store password", null);
        }

        if (oldEndpoint.secure != newEndpoint.secure) {
            content.properties.put("Use https",
                    new String[] { String.valueOf(oldEndpoint.secure), String.valueOf(newEndpoint.secure) });
        }
        if (oldEndpoint.trustAll != newEndpoint.trustAll) {
            content.properties.put("Trust all",
                    new String[] { String.valueOf(oldEndpoint.trustAll), String.valueOf(newEndpoint.trustAll) });
        }

        return content;
    }
}
