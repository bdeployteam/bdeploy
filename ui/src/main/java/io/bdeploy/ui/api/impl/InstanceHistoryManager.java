package io.bdeploy.ui.api.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.util.ManifestComparator;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.StringHelper;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.interfaces.configuration.pcu.ProcessState;
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
import io.bdeploy.ui.dto.HistoryEntryDto;
import io.bdeploy.ui.dto.HistoryEntryDto.HistoryEntryType;
import io.bdeploy.ui.dto.HistoryEntryRuntimeDto;
import io.bdeploy.ui.dto.HistoryFilterDto;
import io.bdeploy.ui.dto.HistoryResultDto;
import jakarta.ws.rs.core.SecurityContext;

public class InstanceHistoryManager {

    private static final Logger log = LoggerFactory.getLogger(InstanceHistoryManager.class);

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

    private List<HistoryEntryDto> loadHistory(InstanceManifest mf) {
        List<HistoryEntryDto> entries = new ArrayList<>();
        String tag = mf.getKey().getTag();
        for (InstanceManifestHistoryRecord rec : mf.getHistory(hive).getFullHistory()) {
            HistoryEntryType type = computeType(rec.action);
            HistoryEntryDto entry = new HistoryEntryDto(rec.timestamp, tag);

            UserInfo userInfo = computeUser(rec.user);
            if (userInfo != null) {
                entry.user = userInfo.name;
                entry.email = userInfo.email;
            }

            entry.title = computeConfigTitle(rec.action, tag);
            entry.type = type;
            entries.add(entry);
        }
        return entries;
    }

    private MasterRuntimeHistoryDto loadRuntimeHistory(MasterProvider mp, String group, String instanceId) {
        RemoteService svc = mp.getControllingMaster(hive, InstanceManifest.load(hive, instanceId, null).getKey());
        MasterRootResource master = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
        MasterNamedResource namedMaster = master.getNamedMaster(group);

        try {
            return namedMaster.getRuntimeHistory(instanceId);
        } catch (Exception e) {
            var r = new MasterRuntimeHistoryDto();
            r.addError("master", "Cannot contact master: " + e.toString());

            if (log.isDebugEnabled()) {
                log.debug("Cannot contact master:", e);
            }

            return r;
        }
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
        for (MinionRuntimeHistoryRecord rec : history.getRecords()) {
            HistoryEntryDto entry = new HistoryEntryDto(rec.timestamp, tag);
            entry.type = HistoryEntryType.RUNTIME;
            entry.runtimeEvent = new HistoryEntryRuntimeDto(minionName, rec.pid, rec.exitCode, rec.state);
            entry.title = computeRuntimeTitle(rec.state, appName);
            UserInfo userInfo = computeUser(rec.user);
            if (userInfo != null) {
                entry.user = userInfo.name;
                entry.email = userInfo.email;
            }
            result.add(entry);
        }
        return result;
    }

    private static String computeConfigTitle(Action action, String tag) {
        return "Version " + tag + ": " + switch (action) {
            case CREATE -> "Created";
            case DELETE -> "Deleted";
            case INSTALL -> "Installed";
            case UNINSTALL -> "Uninstalled";
            case ACTIVATE -> "Activated";
            case DEACTIVATE -> "Deactivated";
            case BANNER_SET -> "Banner set";
            case BANNER_CLEAR -> "Banner removed";
            default -> action.name();
        };
    }

    private static String computeRuntimeTitle(ProcessState state, String process) {
        return process + switch (state) {
            case RUNNING_NOT_STARTED -> " started";
            case RUNNING -> " is alive";
            case RUNNING_UNSTABLE -> " restarted";
            case RUNNING_NOT_ALIVE -> " liveness probe failed";
            case STOPPED -> " stopped";
            case CRASHED_WAITING -> " crashed";
            case CRASHED_PERMANENTLY -> " crashed permanently.";
            case STOPPED_START_PLANNED -> " planned to start";
            case RUNNING_STOP_PLANNED -> " planned to stop";
            default -> ' ' + state.name();
        };
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

    private static HistoryEntryType computeType(Action action) {
        if (action == Action.CREATE || action == Action.DELETE) {
            return HistoryEntryType.CREATE;
        }
        return HistoryEntryType.DEPLOYMENT;
    }
}
