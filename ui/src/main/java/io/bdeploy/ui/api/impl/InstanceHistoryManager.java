package io.bdeploy.ui.api.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.view.ElementView;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ScanOperation;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.dcu.ParameterConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessState;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.history.InstanceManifestHistory.Action;
import io.bdeploy.interfaces.manifest.history.InstanceManifestHistoryRecord;
import io.bdeploy.interfaces.manifest.history.runtime.MinionApplicationRuntimeHistory;
import io.bdeploy.interfaces.manifest.history.runtime.MinionRuntimeHistory;
import io.bdeploy.interfaces.manifest.history.runtime.MinionRuntimeHistoryDto;
import io.bdeploy.interfaces.manifest.history.runtime.MinionRuntimeHistoryRecord;
import io.bdeploy.interfaces.manifest.managed.MasterProvider;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.ui.api.AuthService;
import io.bdeploy.ui.dto.HistoryEntryApplicationDto;
import io.bdeploy.ui.dto.HistoryEntryConfigFilesDto;
import io.bdeploy.ui.dto.HistoryEntryDto;
import io.bdeploy.ui.dto.HistoryEntryDto.HistoryEntryType;
import io.bdeploy.ui.dto.HistoryEntryHttpEndpointDto;
import io.bdeploy.ui.dto.HistoryEntryNodeDto;
import io.bdeploy.ui.dto.HistoryEntryParametersDto;
import io.bdeploy.ui.dto.HistoryEntryRuntimeDto;
import io.bdeploy.ui.dto.HistoryEntryVersionDto;

public class InstanceHistoryManager {

    private final Cache<String, List<HistoryEntryDto>> history = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(5)).maximumSize(10).build();

    @Inject
    private AuthService auth;

    @Context
    private SecurityContext context;

    @Inject
    private MasterProvider mp;

    /**
     * Reads all Instance-History events, saves and returns.<br>
     * Computes all differences between two instance versions in a CREATE event. <br>
     * Returns a specified amount of events. <br>
     *
     * @param instanceId the uuid of the instance
     * @param amount how many events to be loaded
     * @return a list of events
     */
    public List<HistoryEntryDto> getInstanceHistory(BHive hive, String instanceId, int amount, String group) {
        List<HistoryEntryDto> instanceHistory = loadHistory(hive, instanceId, group);
        history.put(instanceId, instanceHistory);

        List<HistoryEntryDto> returnList;

        if (amount < instanceHistory.size()) {
            returnList = instanceHistory.subList(0, amount);
        } else {
            returnList = instanceHistory.subList(0, instanceHistory.size());
        }

        computeNewVersions(hive, instanceId, returnList);
        return returnList;

    }

    /**
     * Loads a specified amount of Events and returns them.<br>
     * Computes all differences between two instance versions in a CREATE event. <br>
     *
     * @param instanceId the uuid of the instance
     * @param amount how many events to load
     * @param offset load events starting from offset
     * @return a list of events
     */
    public List<HistoryEntryDto> getMoreInstanceHistory(BHive hive, String instanceId, String group, int amount, int offset) {

        List<HistoryEntryDto> returnList;
        List<HistoryEntryDto> cachedHistory = getCachedHistory(hive, instanceId, group);

        if (offset < cachedHistory.size()) {
            if (offset + amount < cachedHistory.size()) {
                returnList = cachedHistory.subList(offset, offset + amount);
            } else {
                returnList = cachedHistory.subList(offset, cachedHistory.size());
            }
        } else {
            return new ArrayList<>();
        }

        computeNewVersions(hive, instanceId, returnList);
        return returnList;
    }

    /**
     * Computes the differences between two given instance versions.
     *
     * @param instanceId the instance to compare versions of
     * @param versionA the version tag of the earlier version
     * @param versionB the version tag of the later version
     * @return a {@link HistoryEntryVersionDto} of the computed differences
     */
    public HistoryEntryVersionDto compareVersions(BHive hive, String instanceId, int versionA, int versionB) {
        InstanceManifest a = InstanceManifest.load(hive, instanceId, String.valueOf(versionA));
        InstanceManifest b = InstanceManifest.load(hive, instanceId, String.valueOf(versionB));
        return versionDifferences(hive, a, b);
    }

    public List<HistoryEntryDto> getFilteredInstanceHistory(BHive hive, String instanceId, String group, int amount, int offset,
            String filter) {

        List<HistoryEntryDto> cachedHistory = getCachedHistory(hive, instanceId, group);
        List<HistoryEntryDto> filteredHistory = new ArrayList<>();

        int size = offset + amount;
        filter = filter.trim().toUpperCase();

        for (int i = 0; i < cachedHistory.size() && filteredHistory.size() < size; i++) {
            HistoryEntryDto item = cachedHistory.get(i);
            if (item.title.toUpperCase().contains(filter) || item.user != null && item.user.toUpperCase().contains(filter)) {
                filteredHistory.add(item);
            } else if (item.runtimeEvent != null && item.runtimeEvent.pid != null && item.runtimeEvent.pid.contains(filter)) {
                filteredHistory.add(item);
            }
        }

        if (offset >= filteredHistory.size()) {
            return new ArrayList<>();
        }

        Collections.sort(filteredHistory, (a, b) -> Long.compare(a.timestamp, b.timestamp) * -1);
        filteredHistory = filteredHistory.subList(offset, filteredHistory.size());
        computeNewVersions(hive, instanceId, filteredHistory);

        return filteredHistory;
    }

    private List<HistoryEntryDto> getCachedHistory(BHive hive, String instanceId, String group) {
        try {
            return history.get(instanceId, () -> loadHistory(hive, instanceId, group));
        } catch (ExecutionException e) {
            throw new IllegalStateException("Cannot load history", e);
        }
    }

    // load instance and runtime history
    private List<HistoryEntryDto> loadHistory(BHive hive, String instanceId, String group) {
        List<HistoryEntryDto> instanceHistory = new ArrayList<>();

        // load configuration history
        String rootName = InstanceManifest.getRootName(instanceId);
        List<Manifest.Key> all = new ArrayList<>(hive.execute(new ManifestListOperation().setManifestName(rootName)));

        for (Key manifestKey : all) {
            for (InstanceManifestHistoryRecord record : InstanceManifest.of(hive, manifestKey).getHistory(hive)
                    .getFullHistory()) {

                HistoryEntryDto entry = new HistoryEntryDto(record.timestamp, Integer.parseInt(manifestKey.getTag()));
                if (record.action == Action.CREATE) {
                    entry.type = HistoryEntryType.CREATE;
                } else {
                    entry.type = HistoryEntryType.DEPLOYMENT;
                }

                entry.title = computeConfigTitle(record.action, manifestKey.getTag());
                computeUser(entry, record.user);

                instanceHistory.add(entry);
            }
        }

        // add runtime history
        instanceHistory.addAll(loadRuntimeHistory(hive, instanceId, group));

        Collections.sort(instanceHistory, (a, b) -> Long.compare(a.timestamp, b.timestamp) * -1);
        return instanceHistory;
    }

    private String computeConfigTitle(Action action, String tag) {
        switch (action) {
            case CREATE:
                return "New version: " + tag;
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

    private String computeRuntimeTitle(ProcessState state, String process) {
        switch (state) {
            case CRASHED_PERMANENTLY:
                return "Process " + process + " crashed permanently";
            case CRASHED_WAITING:
                return "Process " + process + " chrashed";
            case RUNNING_STOP_PLANNED:
                return "Stop of " + process + " planned";
            case RUNNING_UNSTABLE:
                return "Restart of process " + process;
            case RUNNING:
                return "Start of process " + process;
            case STOPPED:
                return "Process " + process + " stopped";
            default:
                return "";
        }
    }

    private void computeUser(HistoryEntryDto entry, String user) {
        UserInfo userInfo = null;
        if (user != null && !user.isBlank()) {
            while (user.length() > 2 && user.charAt(0) == '[' && user.charAt(user.length() - 1) == ']') {
                user = user.substring(1, user.length() - 1);
            }
            userInfo = auth.getUser(user);
        } else {
            entry.user = null;
            return;
        }
        if (userInfo != null) {
            if (userInfo.email != null && !userInfo.email.isBlank()) {
                userInfo.email = userInfo.email;
            }
            if (userInfo.fullName != null && !userInfo.fullName.isBlank()) {
                entry.user = userInfo.fullName;
            } else {
                entry.user = user;
            }
        } else {
            entry.user = user;
        }
    }

    private void computeNewVersions(BHive hive, String instanceId, List<HistoryEntryDto> entries) {
        for (HistoryEntryDto entry : entries) {
            if (entry.type == HistoryEntryType.CREATE) {
                if (entry.version > 1) {
                    entry.content = versionDifferences(hive,
                            InstanceManifest.load(hive, instanceId, String.valueOf(entry.version - 1)),
                            InstanceManifest.load(hive, instanceId, String.valueOf(entry.version)));
                }
            }
        }
    }

    // load the runtime history
    private List<HistoryEntryDto> loadRuntimeHistory(BHive hive, String instanceId, String group) {
        List<HistoryEntryDto> content = new ArrayList<>();

        RemoteService svc = mp.getControllingMaster(hive, InstanceManifest.load(hive, instanceId, null).getManifest());
        MasterRootResource master = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
        MasterNamedResource namedMaster = master.getNamedMaster(group);

        for (Entry<String, MinionRuntimeHistoryDto> dto : namedMaster.getRuntimeHistory(instanceId).entrySet()) {
            computeMinionRuntimeHistory(content, dto.getKey(), dto.getValue());
        }
        return content;
    }

    private void computeMinionRuntimeHistory(List<HistoryEntryDto> history, String minionName, MinionRuntimeHistoryDto minion) {
        for (Entry<String, MinionRuntimeHistory> version : minion.entrySet()) {
            computeVersionRuntimeHistory(history, minionName, version.getKey(), version.getValue());
        }
    }

    private void computeVersionRuntimeHistory(List<HistoryEntryDto> history, String minionName, String versionTag,
            MinionRuntimeHistory version) {
        for (Entry<String, MinionApplicationRuntimeHistory> application : version.entrySet()) {
            computeApplicationRuntimeHistory(history, minionName, versionTag, application.getKey(), application.getValue());
        }
    }

    private void computeApplicationRuntimeHistory(List<HistoryEntryDto> history, String minionName, String versionTag,
            String applicationName, MinionApplicationRuntimeHistory application) {

        for (MinionRuntimeHistoryRecord record : application.getRecords()) {
            HistoryEntryDto entry = new HistoryEntryDto(record.timestamp, Integer.parseInt(versionTag));

            entry.type = HistoryEntryType.RUNTIME;
            entry.runtimeEvent = new HistoryEntryRuntimeDto(minionName, record.pid, record.state);
            entry.title = computeRuntimeTitle(record.state, applicationName);
            computeUser(entry, record.user);

            history.add(entry);
        }
    }

    private boolean strNotEqual(String a, String b) {
        return a != null && !a.equals(b);
    }

    // compute differences between two versions
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
        if (strNotEqual(oldConfig.description, newConfig.description)) {
            content.properties.put("Description", new String[] { oldConfig.description, newConfig.description });
        }
        if (strNotEqual(oldConfig.name, newConfig.name)) {
            content.properties.put("Name", new String[] { oldConfig.name, newConfig.name });
        }
        if (oldConfig.purpose != null && !oldConfig.purpose.equals(newConfig.purpose)) {
            content.properties.put("Purpose", new String[] { oldConfig.purpose.name(), newConfig.purpose.name() });
        }
        if (oldConfig.product != null && !oldConfig.product.getTag().equals(newConfig.product.getTag())) {
            content.properties.put("Product version", new String[] { oldConfig.product.getTag(), newConfig.product.getTag() });
        }

        content.nodes = computeNodes(hive, oldManifest, newManifest);

        content.configFiles = setConfigFiles(hive, oldConfig, newConfig);

        return content;
    }

    // iterate through nodes and compute deleted, added or changed nodes
    private Map<String, HistoryEntryNodeDto> computeNodes(BHive hive, InstanceManifest oldManifest,
            InstanceManifest newManifest) {
        Map<String, HistoryEntryNodeDto> nodes = new HashMap<>();
        SortedMap<String, Key> oldNodes = new TreeMap<>(oldManifest.getInstanceNodeManifests());
        SortedMap<String, Key> newNodes = new TreeMap<>(newManifest.getInstanceNodeManifests());

        List<String> oldNodeNames = new ArrayList<>(oldNodes.keySet());
        Collections.sort(oldNodeNames);
        List<String> newNodeNames = new ArrayList<>(newNodes.keySet());
        Collections.sort(newNodeNames);

        boolean finished = false;

        for (int i = 0; i <= newNodes.size() && !finished; i++) {

            while (true) {
                if (i >= newNodes.size()) {
                    for (int j = i; j < oldNodes.size(); j++) {
                        nodes.put(oldNodeNames.get(j), deletedNode(hive, oldNodes.get(oldNodeNames.get(j))));
                    }
                    finished = true;
                    break;
                }
                if (i >= oldNodes.size()) {
                    for (int j = i; j < newNodes.size(); j++) {
                        nodes.put(newNodeNames.get(j), addedNode(hive, newNodes.get(newNodeNames.get(j))));
                    }
                    finished = true;
                    break;
                }
                if (compareNodes(hive, nodes, i, oldNodeNames, newNodeNames, oldNodes, newNodes)) {
                    break;
                }
            }
        }
        return nodes;
    }

    private boolean compareNodes(BHive hive, Map<String, HistoryEntryNodeDto> nodes, int index, List<String> oldNodeNames,
            List<String> newNodeNames, Map<String, Key> oldNodes, Map<String, Key> newNodes) {
        int comparison = newNodeNames.get(index).compareTo(oldNodeNames.get(index));

        if (comparison == 0) {

            //this node stayed, so look for changes
            HistoryEntryNodeDto ret = nodeDifferences(hive, oldNodes.get(oldNodeNames.get(index)),
                    newNodes.get(newNodeNames.get(index)));
            if (ret != null) {
                nodes.put(oldNodeNames.get(index), ret);
            }
            return true;
        }
        if (comparison > 0) {

            nodes.put(oldNodeNames.get(index), deletedNode(hive, oldNodes.get(oldNodeNames.get(index))));
            oldNodes.remove(oldNodeNames.get(index));
            oldNodeNames.remove(index);
        } else {
            nodes.put(oldNodeNames.get(index), addedNode(hive, newNodes.get(newNodeNames.get(index))));
            newNodes.remove(newNodeNames.get(index));
            newNodeNames.remove(index);
        }
        return false;
    }

    private HistoryEntryNodeDto addedNode(BHive hive, Key newKey) {
        List<ApplicationConfiguration> apps = InstanceNodeManifest.of(hive, newKey).getConfiguration().applications;

        HistoryEntryNodeDto node = new HistoryEntryNodeDto();

        for (ApplicationConfiguration app : apps) {
            node.added.add(app.name);
        }

        return node;
    }

    private HistoryEntryNodeDto deletedNode(BHive hive, Key oldKey) {
        List<ApplicationConfiguration> apps = InstanceNodeManifest.of(hive, oldKey).getConfiguration().applications;

        HistoryEntryNodeDto node = new HistoryEntryNodeDto();

        for (ApplicationConfiguration app : apps) {
            node.deleted.add(app.name);
        }

        return node;
    }

    // iterate through config files and compute deleted, added or changed files
    private HistoryEntryConfigFilesDto setConfigFiles(BHive hive, InstanceConfiguration oldConfig,
            InstanceConfiguration newConfig) {

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
            computeConfigFiles(hive, configFiles, oldConfigTree, newConfigTree);
        }
        if (configFiles.added.isEmpty() && configFiles.deleted.isEmpty() && configFiles.changed.isEmpty()) {
            return null;
        } else {
            return configFiles;
        }
    }

    private void computeConfigFiles(BHive hive, HistoryEntryConfigFilesDto content, ObjectId oldConfigTree,
            ObjectId newConfigTree) {
        boolean finished = false;
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
                        content.deleted.add(oldConfigFileNames.get(j));
                    }
                    finished = true;
                    break;
                }
                if (i >= oldConfigFiles.size()) {
                    for (int j = i; j < newConfigFiles.size(); j++) {
                        // added file
                        content.added.add(newConfigFileNames.get(j));
                    }
                    finished = true;
                    break;
                }
                if (compareConfigFiles(content, i, oldConfigFiles, newConfigFiles, oldConfigFileNames, newConfigFileNames)) {
                    break;
                }
            }
        }
    }

    private boolean compareConfigFiles(HistoryEntryConfigFilesDto content, int index, Map<String, ElementView> oldConfigFiles,
            Map<String, ElementView> newConfigFiles, List<String> oldConfigFileNames, List<String> newConfigFileNames) {
        int comparison = oldConfigFileNames.get(index).compareTo(oldConfigFileNames.get(index));

        if (comparison == 0) {
            if (!oldConfigFiles.get(oldConfigFileNames.get(index)).getElementId().getId()
                    .equals(newConfigFiles.get(newConfigFileNames.get(index)).getElementId().getId())) {
                content.changed.add(oldConfigFileNames.get(index));
            }
            return true;
        }
        if (comparison > 0) {
            // deleted file
            content.deleted.add(oldConfigFileNames.get(index));
            oldConfigFiles.remove(oldConfigFileNames.get(index));
            oldConfigFileNames.remove(index);
        } else {
            // added file
            content.deleted.add(newConfigFileNames.get(index));
            newConfigFiles.remove(newConfigFileNames.get(index));
            newConfigFileNames.remove(index);
        }
        return false;
    }

    // iterate trough applications and compute deleted, added or changed applications
    private HistoryEntryNodeDto nodeDifferences(BHive hive, Key oldKey, Key newKey) {
        HistoryEntryNodeDto content = new HistoryEntryNodeDto();

        InstanceNodeConfiguration oldNodeConfig = InstanceNodeManifest.of(hive, oldKey).getConfiguration();
        InstanceNodeConfiguration newNodeConfig = InstanceNodeManifest.of(hive, newKey).getConfiguration();

        List<ApplicationConfiguration> oldApplications = new ArrayList<>(oldNodeConfig.applications);
        Collections.sort(oldApplications, (a, b) -> a.uid.compareTo(b.uid));

        List<ApplicationConfiguration> newApplications = new ArrayList<>(newNodeConfig.applications);
        Collections.sort(newApplications, (a, b) -> a.uid.compareTo(b.uid));

        boolean finished = false;

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
                if (compareApplications(content, i, oldApplications, newApplications)) {
                    break;
                }
            }
        }
        if (content.added.isEmpty() && content.deleted.isEmpty() && content.changed.isEmpty()) {
            return null;
        }
        return content;
    }

    private boolean compareApplications(HistoryEntryNodeDto content, int index, List<ApplicationConfiguration> oldApplications,
            List<ApplicationConfiguration> newApplications) {
        int comparison = newApplications.get(index).uid.compareTo(oldApplications.get(index).uid);

        if (comparison == 0) {
            HistoryEntryApplicationDto ret = applicationDifferences(oldApplications.get(index), newApplications.get(index));
            if (ret != null) {
                content.changed.put(oldApplications.get(index).name, ret);
            }

            return true;
        } else if (comparison > 0) {
            content.deleted.add(oldApplications.get(index).name);
            oldApplications.remove(index);
        } else {
            content.added.add(newApplications.get(index).name);
            newApplications.remove(index);
        }
        return false;
    }

    // compute differences between two applications
    private HistoryEntryApplicationDto applicationDifferences(ApplicationConfiguration oldConfig,
            ApplicationConfiguration newConfig) {
        HistoryEntryApplicationDto content = new HistoryEntryApplicationDto();

        compareApplicationProperties(content, oldConfig, newConfig);

        content.parameters = computeParameters(oldConfig, newConfig);

        computeHttpEndpoints(content, oldConfig, newConfig);

        if (content.addedEndpoints.isEmpty() && content.deletedEndpoints.isEmpty() && content.endpoints.isEmpty()
                && content.parameters == null && content.processControlProperties.isEmpty() && content.properties.isEmpty()) {
            return null;
        }
        return content;

    }

    private void compareApplicationProperties(HistoryEntryApplicationDto content, ApplicationConfiguration oldConfig,
            ApplicationConfiguration newConfig) {
        if (strNotEqual(oldConfig.name, newConfig.name)) {
            content.properties.put("Name", new String[] { oldConfig.name, newConfig.name });
        }
        if (strNotEqual(oldConfig.start.executable, newConfig.start.executable)) {
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
    }

    // iterate through parameters and compute deleted, added or changed parameters
    private HistoryEntryParametersDto computeParameters(ApplicationConfiguration oldConfig, ApplicationConfiguration newConfig) {
        HistoryEntryParametersDto parameters = new HistoryEntryParametersDto();

        List<ParameterConfiguration> oldParameters = oldConfig.start.parameters;
        List<ParameterConfiguration> newParameters = newConfig.start.parameters;

        Collections.sort(oldParameters, (a, b) -> a.uid.compareTo(b.uid));
        Collections.sort(newParameters, (a, b) -> a.uid.compareTo(b.uid));

        boolean finished = false;

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
                if (compareParameters(parameters, i, oldParameters, newParameters)) {
                    break;
                }

            }
        }
        if (parameters.added.isEmpty() && parameters.changed.isEmpty() && parameters.deleted.isEmpty()) {
            return null;
        }
        return parameters;
    }

    private boolean compareParameters(HistoryEntryParametersDto parameters, int index, List<ParameterConfiguration> oldParameters,
            List<ParameterConfiguration> newParameters) {
        int comparison = newParameters.get(index).uid.compareTo(oldParameters.get(index).uid);

        if (comparison == 0) {
            if (!oldParameters.get(index).value.equals(newParameters.get(index).value)) {
                parameters.changed.put(oldParameters.get(index).uid,
                        new String[] { oldParameters.get(index).value, newParameters.get(index).value });
            }
            return true;
        }
        if (comparison > 0) {
            parameters.deleted.add(new String[] { oldParameters.get(index).uid, oldParameters.get(index).value });
            oldParameters.remove(index);
        } else {
            parameters.added.add(new String[] { newParameters.get(index).uid, newParameters.get(index).value });
            newParameters.remove(index);
        }
        return false;
    }

    // iterate through http endpoints and compute deleted, added or changed endpoints
    private void computeHttpEndpoints(HistoryEntryApplicationDto content, ApplicationConfiguration oldConfig,
            ApplicationConfiguration newConfig) {
        // iterate trough http endpoints
        List<HttpEndpoint> oldEndpoints = oldConfig.endpoints.http;
        List<HttpEndpoint> newEndpoints = newConfig.endpoints.http;

        Collections.sort(oldEndpoints, (a, b) -> a.id.compareTo(b.id));
        Collections.sort(newEndpoints, (a, b) -> a.id.compareTo(b.id));

        boolean finished = false;

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
                if (compareHttpEndpoints(content, i, oldEndpoints, newEndpoints)) {
                    break;
                }

            }
        }
    }

    private boolean compareHttpEndpoints(HistoryEntryApplicationDto content, int index, List<HttpEndpoint> oldEndpoints,
            List<HttpEndpoint> newEndpoints) {
        int comparison = newEndpoints.get(index).id.compareTo(oldEndpoints.get(index).id);

        if (comparison == 0) {
            HistoryEntryHttpEndpointDto ret = endpointDifferences(oldEndpoints.get(index), newEndpoints.get(index));
            if (!ret.properties.isEmpty()) {
                content.endpoints.put(oldEndpoints.get(index).path + ":" + oldEndpoints.get(index).port, ret);
            }
            return true;
        }
        if (comparison > 0) {
            content.deletedEndpoints.add(oldEndpoints.get(index).path + ":" + oldEndpoints.get(index).port);
            oldEndpoints.remove(index);
        } else {
            content.addedEndpoints.add(newEndpoints.get(index).path + ":" + newEndpoints.get(index).port);
            newEndpoints.remove(index);
        }
        return false;
    }

    // compare differences between two http endpoints
    private HistoryEntryHttpEndpointDto endpointDifferences(HttpEndpoint oldEndpoint, HttpEndpoint newEndpoint) {

        HistoryEntryHttpEndpointDto content = new HistoryEntryHttpEndpointDto();

        // test for differences in http endpoint properties

        if (strNotEqual(oldEndpoint.authPass, newEndpoint.authPass)) {
            content.properties.put("Authentication password", null);
        }
        if (strNotEqual(oldEndpoint.authUser, newEndpoint.authUser)) {
            content.properties.put("User", new String[] { oldEndpoint.authUser, newEndpoint.authUser });
        }
        if (oldEndpoint.authType != newEndpoint.authType) {
            content.properties.put("Authentication type",
                    new String[] { oldEndpoint.authType.name(), newEndpoint.authType.name() });
        }
        if (strNotEqual(oldEndpoint.path, newEndpoint.path)) {
            content.properties.put("Path", new String[] { oldEndpoint.path, newEndpoint.path });
        }
        if (strNotEqual(oldEndpoint.port, newEndpoint.port)) {
            content.properties.put("Port", new String[] { oldEndpoint.port, newEndpoint.port });
        }
        if (strNotEqual(oldEndpoint.trustStore, newEndpoint.trustStore)) {
            content.properties.put("Trust-store path", new String[] { oldEndpoint.trustStore, newEndpoint.trustStore });
        }
        if (strNotEqual(oldEndpoint.trustStorePass, newEndpoint.trustStorePass)) {
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
