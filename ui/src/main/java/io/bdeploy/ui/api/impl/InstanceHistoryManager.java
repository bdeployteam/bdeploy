package io.bdeploy.ui.api.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.ws.rs.core.SecurityContext;

import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.view.ElementView;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ScanOperation;
import io.bdeploy.bhive.util.ManifestComparator;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.StringHelper;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.dcu.ParameterConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessState;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.history.InstanceManifestHistory.Action;
import io.bdeploy.interfaces.manifest.history.InstanceManifestHistoryRecord;
import io.bdeploy.interfaces.manifest.history.runtime.MasterRuntimeHistoryDto;
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
import io.bdeploy.ui.dto.HistoryFilterDto;
import io.bdeploy.ui.dto.HistoryResultDto;

public class InstanceHistoryManager {

    private final BHive hive;
    private final AuthService auth;
    private final SecurityContext context;
    private final MasterProvider mp;

    public InstanceHistoryManager(AuthService auth, SecurityContext context, MasterProvider mp, BHive hive) {
        this.auth = auth;
        this.context = context;
        this.mp = mp;
        this.hive = hive;
    }

    /**
     * Returns the changes made to an instance.
     */
    public HistoryResultDto getInstanceHistory(String group, String instanceId, HistoryFilterDto filter) {
        HistoryResultDto result = new HistoryResultDto();

        // Determine all versions that are available
        String rootName = InstanceManifest.getRootName(instanceId);
        List<Manifest.Key> all = new ArrayList<>(hive.execute(new ManifestListOperation().setManifestName(rootName)));
        Collections.sort(all, ManifestComparator.NEWEST_FIRST);

        // Determine the range of versions to inspect
        int firstIdx = StringHelper.isNullOrEmpty(filter.startTag) ? 0 : all.indexOf(new Manifest.Key(rootName, filter.startTag));
        List<Manifest.Key> subList = all.subList(firstIdx, all.size());

        MasterRuntimeHistoryDto runtimeHistory = new MasterRuntimeHistoryDto();
        if (filter.showRuntimeEvents) {
            runtimeHistory = loadRuntimeHistory(mp, group, instanceId);
        }

        // Load and compute differences
        for (int i = 0; i < subList.size(); i++) {
            // Skip inspecting the next entry if we have enough events
            if (result.events.size() > filter.maxResults) {
                break;
            }

            Manifest.Key key = subList.get(i);
            Manifest.Key nextKey = (i + 1) < (subList.size()) ? subList.get(i + 1) : null;
            result.next = nextKey != null ? nextKey.getTag() : null;

            // Load history
            InstanceManifest manifest = InstanceManifest.load(hive, instanceId, key.getTag());
            List<HistoryEntryDto> events = loadHistory(manifest);

            // Compute difference to previous version
            Optional<HistoryEntryDto> create = events.stream().filter(e -> e.type == HistoryEntryType.CREATE).findFirst();
            if (create.isPresent() && nextKey != null) {
                InstanceManifest nextManifest = InstanceManifest.load(hive, instanceId, nextKey.getTag());
                create.get().content = compareManifests(nextManifest, manifest);
            }
            result.addAll(events, filter);

            // Append all runtime events from this version
            for (HistoryEntryDto runtimeEvent : getRuntimeHistory(runtimeHistory, key.getTag())) {
                result.add(runtimeEvent, filter);
            }
        }

        // Check runtime history for errors (minion offline)
        for (Map.Entry<String, String> entry : runtimeHistory.getMinion2Error().entrySet()) {
            result.errors.add(entry.getKey() + ": " + entry.getValue());
        }

        // Sort by creation time
        Collections.sort(result.events, (a, b) -> Long.compare(a.timestamp, b.timestamp) * -1);
        return result;
    }

    /**
     * Computes the differences between two given instance configurations.
     *
     * @param configA
     *            the first configuration to be compared.
     * @param configB
     *            the second configuration of the later version
     * @return the computed differences
     */
    public HistoryEntryVersionDto compare(InstanceConfigurationDto configA, InstanceConfigurationDto configB) {
        HistoryEntryVersionDto content = new HistoryEntryVersionDto();

        Map<String, InstanceNodeConfiguration> node2ConfigA = configA.nodeDtos.stream().collect(Collectors.toMap(c -> c.nodeName,
                c -> c.nodeConfiguration == null ? new InstanceNodeConfiguration() : c.nodeConfiguration));
        Map<String, InstanceNodeConfiguration> node2ConfigB = configB.nodeDtos.stream().collect(Collectors.toMap(c -> c.nodeName,
                c -> c.nodeConfiguration == null ? new InstanceNodeConfiguration() : c.nodeConfiguration));
        content.properties = compareInstanceConfiguration(configA.config, configB.config);
        content.nodes = compareInstanceNodeConfiguration(node2ConfigA, node2ConfigB);

        return content;
    }

    /**
     * Computes the differences between two given instance manifests.
     *
     * @param mfA
     *            the first manifest to be compared.
     * @param mfB
     *            the second manifest of the later version
     * @return the computed differences
     */
    public HistoryEntryVersionDto compareManifests(InstanceManifest mfA, InstanceManifest mfB) {
        HistoryEntryVersionDto content = new HistoryEntryVersionDto();

        InstanceConfiguration configA = mfA.getConfiguration();
        InstanceConfiguration configB = mfB.getConfiguration();
        Map<String, InstanceNodeConfiguration> node2ConfigA = mfA.getInstanceNodeConfiguration(hive);
        Map<String, InstanceNodeConfiguration> node2ConfigB = mfB.getInstanceNodeConfiguration(hive);

        content.properties = compareInstanceConfiguration(configA, configB);
        content.nodes = compareInstanceNodeConfiguration(node2ConfigA, node2ConfigB);
        content.configFiles = compareConfigFiles(configA, configB);

        return content;
    }

    private List<HistoryEntryDto> loadHistory(InstanceManifest mf) {
        List<HistoryEntryDto> entries = new ArrayList<>();
        String tag = mf.getManifest().getTag();
        for (InstanceManifestHistoryRecord record : mf.getHistory(hive).getFullHistory()) {
            HistoryEntryType type = computeType(record.action);
            HistoryEntryDto entry = new HistoryEntryDto(record.timestamp, tag);

            UserInfo userInfo = computeUser(record.user);
            if (userInfo != null) {
                entry.user = userInfo.name;
                entry.email = userInfo.email;
            }

            entry.title = computeConfigTitle(record.action, tag);
            entry.type = type;
            entries.add(entry);
        }
        return entries;
    }

    private MasterRuntimeHistoryDto loadRuntimeHistory(MasterProvider mp, String group, String instanceId) {
        RemoteService svc = mp.getControllingMaster(hive, InstanceManifest.load(hive, instanceId, null).getManifest());
        MasterRootResource master = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
        MasterNamedResource namedMaster = master.getNamedMaster(group);
        return namedMaster.getRuntimeHistory(instanceId);
    }

    private List<HistoryEntryDto> getRuntimeHistory(MasterRuntimeHistoryDto history, String tag) {
        List<HistoryEntryDto> result = new ArrayList<>();

        Map<String, MinionRuntimeHistoryDto> minion2History = history.getMinion2History();
        for (Map.Entry<String, MinionRuntimeHistoryDto> entry : minion2History.entrySet()) {
            String minion = entry.getKey();
            MinionRuntimeHistoryDto minionHistoryDto = entry.getValue();
            MinionRuntimeHistory minionHistory = minionHistoryDto.get(tag);
            if (minionHistory == null) {
                continue;
            }
            result.addAll(getMinionRuntimeHistory(minion, tag, minionHistory));
        }
        return result;
    }

    private Collection<HistoryEntryDto> getMinionRuntimeHistory(String minion, String tag, MinionRuntimeHistory minionHistory) {
        List<HistoryEntryDto> result = new ArrayList<>();
        for (Map.Entry<String, MinionApplicationRuntimeHistory> entry : minionHistory.getHistory().entrySet()) {
            String appName = entry.getKey();
            MinionApplicationRuntimeHistory appHistory = entry.getValue();
            result.addAll(getApplicationRuntimeHistory(minion, tag, appName, appHistory));
        }
        return result;
    }

    private List<HistoryEntryDto> getApplicationRuntimeHistory(String minionName, String tag, String appName,
            MinionApplicationRuntimeHistory history) {
        List<HistoryEntryDto> result = new ArrayList<>();
        for (MinionRuntimeHistoryRecord record : history.getRecords()) {
            HistoryEntryDto entry = new HistoryEntryDto(record.timestamp, tag);
            entry.type = HistoryEntryType.RUNTIME;
            entry.runtimeEvent = new HistoryEntryRuntimeDto(minionName, record.pid, record.exitCode, record.state);
            entry.title = computeRuntimeTitle(record.state, appName);
            UserInfo userInfo = computeUser(record.user);
            if (userInfo != null) {
                entry.user = userInfo.name;
                entry.email = userInfo.email;
            }
            result.add(entry);
        }
        return result;
    }

    private Map<String, String[]> compareInstanceConfiguration(InstanceConfiguration configA, InstanceConfiguration configB) {
        Map<String, String[]> changes = new TreeMap<>();
        if (configA.autoStart != configB.autoStart) {
            changes.put("Auto-start", new String[] { String.valueOf(configA.autoStart), String.valueOf(configB.autoStart) });
        }
        if (configA.autoUninstall != configB.autoUninstall) {
            changes.put("Auto-uninstall",
                    new String[] { String.valueOf(configA.autoUninstall), String.valueOf(configB.autoUninstall) });
        }
        if (StringHelper.notEqual(configA.description, configB.description)) {
            changes.put("Description", new String[] { configA.description, configB.description });
        }
        if (StringHelper.notEqual(configA.name, configB.name)) {
            changes.put("Name", new String[] { configA.name, configB.name });
        }
        if (configA.purpose != null && !configA.purpose.equals(configB.purpose)) {
            changes.put("Purpose", new String[] { configA.purpose.name(), configB.purpose.name() });
        }
        if (configA.product != null && !configA.product.getTag().equals(configB.product.getTag())) {
            changes.put("Product version", new String[] { configA.product.getTag(), configB.product.getTag() });
        }
        return changes;
    }

    // iterate through nodes and compute deleted, added or changed nodes
    private Map<String, HistoryEntryNodeDto> compareInstanceNodeConfiguration(Map<String, InstanceNodeConfiguration> node2ConfigA,
            Map<String, InstanceNodeConfiguration> node2ConfigB) {
        Map<String, HistoryEntryNodeDto> nodes = new HashMap<>();

        // Compare differences
        SetView<String> matching = Sets.intersection(node2ConfigA.keySet(), node2ConfigB.keySet());
        for (String nodeId : matching) {
            InstanceNodeConfiguration configA = node2ConfigA.get(nodeId);
            InstanceNodeConfiguration configB = node2ConfigB.get(nodeId);
            HistoryEntryNodeDto ret = compareInstanceNodeConfiguration(configA, configB);
            if (ret != null) {
                nodes.put(nodeId, ret);
            }
        }

        // Deleted
        SetView<String> deleted = Sets.difference(node2ConfigA.keySet(), node2ConfigB.keySet());
        for (String nodeId : deleted) {
            List<ApplicationConfiguration> apps = node2ConfigA.get(nodeId).applications;
            HistoryEntryNodeDto node = new HistoryEntryNodeDto();
            for (ApplicationConfiguration app : apps) {
                node.deleted.add(app.name);
            }

            nodes.put(nodeId, node);
        }

        // Added
        SetView<String> added = Sets.difference(node2ConfigB.keySet(), node2ConfigA.keySet());
        for (String nodeId : added) {
            List<ApplicationConfiguration> apps = node2ConfigB.get(nodeId).applications;
            HistoryEntryNodeDto node = new HistoryEntryNodeDto();
            for (ApplicationConfiguration app : apps) {
                node.added.add(app.name);
            }
            nodes.put(nodeId, node);
        }
        return nodes;
    }

    // iterate through config files and compute deleted, added or changed files
    private HistoryEntryConfigFilesDto compareConfigFiles(InstanceConfiguration configA, InstanceConfiguration configB) {

        HistoryEntryConfigFilesDto configFiles = new HistoryEntryConfigFilesDto();

        ObjectId configTreeA = configA.configTree;
        ObjectId configTreeB = configB.configTree;

        if (configTreeA == null) {
            if (configTreeB != null) {
                for (String name : hive.execute(new ScanOperation().setTree(configTreeB)).getChildren().keySet()) {
                    configFiles.added.add(name);
                }
            }
        } else if (configTreeB == null) {
            for (String name : hive.execute(new ScanOperation().setTree(configTreeA)).getChildren().keySet()) {
                configFiles.deleted.add(name);
            }
        } else {
            compareConfigFileTree(configFiles, configTreeA, configTreeB);
        }

        // Check if something has been changed
        if (configFiles.added.isEmpty() && configFiles.deleted.isEmpty() && configFiles.changed.isEmpty()) {
            return null;
        }
        return configFiles;
    }

    private void compareConfigFileTree(HistoryEntryConfigFilesDto content, ObjectId configTreeA, ObjectId configTreeB) {
        Map<String, ElementView> configFilesA = new HashMap<>(
                hive.execute(new ScanOperation().setTree(configTreeA)).getChildren());
        Map<String, ElementView> configFilesB = new HashMap<>(
                hive.execute(new ScanOperation().setTree(configTreeB)).getChildren());

        // Compare differences
        SetView<String> matching = Sets.intersection(configFilesA.keySet(), configFilesB.keySet());
        for (String fileName : matching) {
            ElementView viewA = configFilesA.get(fileName);
            ElementView viewB = configFilesB.get(fileName);
            if (!viewA.getElementId().getId().equals(viewB.getElementId().getId())) {
                content.changed.add(fileName);
            }
        }

        // Deleted
        SetView<String> deleted = Sets.difference(configFilesA.keySet(), configFilesB.keySet());
        for (String fileName : deleted) {
            content.deleted.add(fileName);
        }

        // Added
        SetView<String> added = Sets.difference(configFilesB.keySet(), configFilesA.keySet());
        for (String fileName : added) {
            content.added.add(fileName);
        }
    }

    // iterate trough applications and compute deleted, added or changed applications
    private HistoryEntryNodeDto compareInstanceNodeConfiguration(InstanceNodeConfiguration nodeConfigA,
            InstanceNodeConfiguration newNodeConfig) {
        HistoryEntryNodeDto content = new HistoryEntryNodeDto();

        Map<String, ApplicationConfiguration> appsA = nodeConfigA.applications.stream()
                .collect(Collectors.toMap(a -> a.uid, a -> a));
        Map<String, ApplicationConfiguration> appsB = newNodeConfig.applications.stream()
                .collect(Collectors.toMap(a -> a.uid, a -> a));

        // Compare differences
        SetView<String> matching = Sets.intersection(appsA.keySet(), appsB.keySet());
        for (String appId : matching) {
            ApplicationConfiguration configA = appsA.get(appId);
            ApplicationConfiguration configB = appsB.get(appId);
            HistoryEntryApplicationDto ret = compareApplicationConfiguration(configA, configB);
            if (ret != null) {
                content.changed.put(configB.name, ret);
            }
        }

        // Deleted
        SetView<String> deleted = Sets.difference(appsA.keySet(), appsB.keySet());
        for (String appId : deleted) {
            ApplicationConfiguration appConfig = appsA.get(appId);
            content.deleted.add(appConfig.name);
        }

        // Added
        SetView<String> added = Sets.difference(appsB.keySet(), appsA.keySet());
        for (String appId : added) {
            ApplicationConfiguration appConfig = appsB.get(appId);
            content.added.add(appConfig.name);
        }

        // Check if something has been changed
        if (content.added.isEmpty() && content.deleted.isEmpty() && content.changed.isEmpty()) {
            return null;
        }
        return content;
    }

    // compute differences between two applications
    private HistoryEntryApplicationDto compareApplicationConfiguration(ApplicationConfiguration configA,
            ApplicationConfiguration configB) {
        HistoryEntryApplicationDto content = new HistoryEntryApplicationDto();

        content.parameters = compareParameters(configA, configB);
        content.properties = compareApplicationProperties(configA, configB);
        compareHttpEndpoints(content, configA, configB);

        // Check if something has been changed
        if (content.addedEndpoints.isEmpty() && content.deletedEndpoints.isEmpty() && content.changedEndpoints.isEmpty()
                && content.parameters == null && content.processControlProperties.isEmpty() && content.properties.isEmpty()) {
            return null;
        }

        return content;

    }

    private Map<String, String[]> compareApplicationProperties(ApplicationConfiguration configA,
            ApplicationConfiguration configB) {
        Map<String, String[]> changes = new TreeMap<>();
        if (StringHelper.notEqual(configA.name, configB.name)) {
            changes.put("Name", new String[] { configA.name, configB.name });
        }
        if (StringHelper.notEqual(configA.start.executable, configB.start.executable)) {
            changes.put("Executable path", new String[] { configA.start.executable, configB.start.executable });
        }

        if (configA.processControl.attachStdin != configB.processControl.attachStdin) {
            changes.put("Attach to stdin", new String[] { String.valueOf(configA.processControl.attachStdin),
                    String.valueOf(configB.processControl.attachStdin) });
        }
        if (configA.processControl.keepAlive != configB.processControl.keepAlive) {
            changes.put("Keep alive", new String[] { String.valueOf(configA.processControl.keepAlive),
                    String.valueOf(configB.processControl.keepAlive) });
        }
        if (configA.processControl.gracePeriod != configB.processControl.gracePeriod) {
            changes.put("Grace period", new String[] { String.valueOf(configA.processControl.gracePeriod),
                    String.valueOf(configB.processControl.gracePeriod) });
        }
        if (configA.processControl.noOfRetries != configB.processControl.noOfRetries) {
            changes.put("Number of retries", new String[] { String.valueOf(configA.processControl.noOfRetries),
                    String.valueOf(configB.processControl.noOfRetries) });
        }

        if (configA.processControl.startType != null
                && !configA.processControl.startType.equals(configB.processControl.startType)) {
            changes.put("Start type",
                    new String[] { configA.processControl.startType.name(), configB.processControl.startType.name() });
        }
        return changes;
    }

    // iterate through parameters and compute deleted, added or changed parameters
    private HistoryEntryParametersDto compareParameters(ApplicationConfiguration configA, ApplicationConfiguration configB) {
        HistoryEntryParametersDto parameters = new HistoryEntryParametersDto();

        Map<String, ParameterConfiguration> parametersA = configA.start.parameters.stream()
                .collect(Collectors.toMap(p -> p.uid, p -> p));
        Map<String, ParameterConfiguration> parametersB = configB.start.parameters.stream()
                .collect(Collectors.toMap(p -> p.uid, p -> p));

        // Compare differences
        SetView<String> matching = Sets.intersection(parametersA.keySet(), parametersB.keySet());
        for (String appId : matching) {
            ParameterConfiguration paramConfigA = parametersA.get(appId);
            ParameterConfiguration paramConfigB = parametersB.get(appId);
            if (StringHelper.notEqual(paramConfigA.value, paramConfigB.value)) {
                parameters.changed.put(paramConfigA.uid, new String[] { paramConfigA.value, paramConfigB.value });
            }
        }

        // Deleted
        SetView<String> deleted = Sets.difference(parametersA.keySet(), parametersB.keySet());
        for (String appId : deleted) {
            ParameterConfiguration paramConfig = parametersA.get(appId);
            parameters.deleted.add(new String[] { paramConfig.uid, paramConfig.value });
        }

        // Added
        SetView<String> added = Sets.difference(parametersB.keySet(), parametersA.keySet());
        for (String appId : added) {
            ParameterConfiguration paramConfig = parametersB.get(appId);
            parameters.added.add(new String[] { paramConfig.uid, paramConfig.value });
        }

        // Check if something has been changed
        if (parameters.added.isEmpty() && parameters.changed.isEmpty() && parameters.deleted.isEmpty()) {
            return null;
        }
        return parameters;
    }

    // iterate through http endpoints and compute deleted, added or changed endpoints
    private void compareHttpEndpoints(HistoryEntryApplicationDto content, ApplicationConfiguration configA,
            ApplicationConfiguration configB) {

        Map<String, HttpEndpoint> endpointsA = configA.endpoints.http.stream().collect(Collectors.toMap(e -> e.id, e -> e));
        Map<String, HttpEndpoint> endpointsB = configB.endpoints.http.stream().collect(Collectors.toMap(e -> e.id, e -> e));

        // Compare differences
        SetView<String> matching = Sets.intersection(endpointsA.keySet(), endpointsB.keySet());
        for (String id : matching) {
            HttpEndpoint endpointConfigA = endpointsA.get(id);
            HttpEndpoint endpointConfigB = endpointsB.get(id);
            HistoryEntryHttpEndpointDto ret = endpointDifferences(endpointConfigA, endpointConfigB);
            if (!ret.properties.isEmpty()) {
                content.changedEndpoints.put(endpointConfigA.path + ":" + endpointConfigA.port, ret);
            }
        }

        // Deleted
        SetView<String> deleted = Sets.difference(endpointsA.keySet(), endpointsB.keySet());
        for (String id : deleted) {
            HttpEndpoint endpoint = endpointsA.get(id);
            content.deletedEndpoints.add(endpoint.path + ":" + endpoint.port);
        }

        // Added
        SetView<String> added = Sets.difference(endpointsB.keySet(), endpointsA.keySet());
        for (String id : added) {
            HttpEndpoint endpoint = endpointsB.get(id);
            content.addedEndpoints.add(endpoint.path + ":" + endpoint.port);
        }
    }

    // compare differences between two http endpoints
    private HistoryEntryHttpEndpointDto endpointDifferences(HttpEndpoint endpointA, HttpEndpoint endpointB) {

        HistoryEntryHttpEndpointDto content = new HistoryEntryHttpEndpointDto();

        // test for differences in http endpoint properties
        if (StringHelper.notEqual(endpointA.authPass, endpointB.authPass)) {
            content.properties.put("Authentication password", null);
        }
        if (StringHelper.notEqual(endpointA.authUser, endpointB.authUser)) {
            content.properties.put("User", new String[] { endpointA.authUser, endpointB.authUser });
        }
        if (endpointA.authType != endpointB.authType) {
            content.properties.put("Authentication type", new String[] { endpointA.authType.name(), endpointB.authType.name() });
        }
        if (StringHelper.notEqual(endpointA.path, endpointB.path)) {
            content.properties.put("Path", new String[] { endpointA.path, endpointB.path });
        }
        if (StringHelper.notEqual(endpointA.port, endpointB.port)) {
            content.properties.put("Port", new String[] { endpointA.port, endpointB.port });
        }
        if (StringHelper.notEqual(endpointA.trustStore, endpointB.trustStore)) {
            content.properties.put("Trust-store path", new String[] { endpointA.trustStore, endpointB.trustStore });
        }
        if (StringHelper.notEqual(endpointA.trustStorePass, endpointB.trustStorePass)) {
            content.properties.put("Trust-store password", null);
        }

        if (endpointA.secure != endpointB.secure) {
            content.properties.put("Use https",
                    new String[] { String.valueOf(endpointA.secure), String.valueOf(endpointB.secure) });
        }
        if (endpointA.trustAll != endpointB.trustAll) {
            content.properties.put("Trust all",
                    new String[] { String.valueOf(endpointA.trustAll), String.valueOf(endpointB.trustAll) });
        }

        return content;
    }

    private String computeConfigTitle(Action action, String tag) {
        switch (action) {
            case CREATE:
                return "Version " + tag + ": Created";
            case INSTALL:
                return "Version " + tag + ": Installed";
            case UNINSTALL:
                return "Version " + tag + ": Uninstalled";
            case ACTIVATE:
                return "Version " + tag + ": Activated";
            case DEACTIVATE:
                return "Version " + tag + ": Deactivated";
            default:
                return "";
        }
    }

    private String computeRuntimeTitle(ProcessState state, String process) {
        switch (state) {
            case RUNNING:
                return process + " started";
            case RUNNING_UNSTABLE:
                return process + " restarted";
            case STOPPED:
                return process + " stopped";
            case CRASHED_WAITING:
                return process + " crashed";
            case CRASHED_PERMANENTLY:
                return process + " crashed permanently.";
            default:
                return "";
        }
    }

    private UserInfo computeUser(String user) {
        if (user == null || user.isBlank()) {
            return null;
        }
        // See JerseyOnBehalfOfFilter
        if (user.startsWith("[") && user.endsWith("]")) {
            user = user.substring(1, user.length() - 1);
        }

        // Complete information based on the stored user
        UserInfo userInfo = auth.getUser(user);
        if (userInfo == null) {
            return new UserInfo(user, false);
        }
        return userInfo;
    }

    private HistoryEntryType computeType(Action action) {
        if (action == Action.CREATE) {
            return HistoryEntryType.CREATE;
        }
        return HistoryEntryType.DEPLOYMENT;
    }
}
