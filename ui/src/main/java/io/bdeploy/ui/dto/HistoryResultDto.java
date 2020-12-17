package io.bdeploy.ui.dto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import io.bdeploy.common.util.StringHelper;
import io.bdeploy.ui.api.impl.InstanceHistoryManager;
import io.bdeploy.ui.dto.HistoryEntryDto.HistoryEntryType;

/**
 * Result object of the {@linkplain InstanceHistoryManager}
 */
public class HistoryResultDto {

    /**
     * The next manifest version not included in the history. Can be used for a follow-up request to get more events.
     */
    public String next;

    /**
     * The history events
     */
    public List<HistoryEntryDto> events = new ArrayList<>();

    /**
     * List of errors when computing the history
     */
    public List<String> errors = new ArrayList<>();

    /**
     * Adds the all events matching the given filter.
     */
    public void addAll(Collection<HistoryEntryDto> dtos, HistoryFilterDto filter) {
        for (HistoryEntryDto dto : dtos) {
            add(dto, filter);
        }
    }

    /**
     * Adds the given event to the result list if they match the given filter.
     */
    public void add(HistoryEntryDto dto, HistoryFilterDto filter) {
        if (!matches(dto, filter)) {
            return;
        }
        events.add(dto);
    }

    private boolean matches(HistoryEntryDto dto, HistoryFilterDto filter) {
        // Skip events of the wrong type
        if (dto.type == HistoryEntryType.CREATE && !filter.showCreateEvents) {
            return false;
        }
        if (dto.type == HistoryEntryType.DEPLOYMENT && !filter.showDeploymentEvents) {
            return false;
        }
        if (dto.type == HistoryEntryType.RUNTIME && !filter.showRuntimeEvents) {
            return false;
        }

        // No filter -> just add
        String text = filter.filterText;
        if (StringHelper.isNullOrEmpty(text)) {
            return true;
        }

        // Match user and mail
        if (dto.user != null && contains(dto.user, text)) {
            return true;
        }
        if (dto.email != null && contains(dto.user, text)) {
            return true;
        }

        // Match title
        if (dto.title != null && contains(dto.title, text)) {
            return true;
        }

        // Match ID
        HistoryEntryRuntimeDto runtime = dto.runtimeEvent;
        if (runtime != null) {
            String pid = String.valueOf(runtime.pid);
            if (pid.equals(text)) {
                return true;
            }
        }

        // Search in configured application
        HistoryEntryVersionDto content = dto.content;
        if (content != null && content.nodes != null) {
            for (Map.Entry<String, HistoryEntryNodeDto> entry : content.nodes.entrySet()) {
                HistoryEntryNodeDto value = entry.getValue();
                if (matchesApp(value, text)) {
                    return true;
                }
            }
        }

        // No match found
        return false;
    }

    private boolean matchesApp(HistoryEntryNodeDto value, String filter) {
        // Check for added / deleted applications
        if (matches(value.added, filter)) {
            return true;
        }
        if (matches(value.deleted, filter)) {
            return true;
        }

        // Check for changed parameters
        for (Map.Entry<String, HistoryEntryApplicationDto> entry : value.changed.entrySet()) {
            // Check the name of the application
            String name = entry.getKey();
            if (contains(name, filter)) {
                return true;
            }

            // Check the added, changed and deleted parameters
            HistoryEntryApplicationDto dto = entry.getValue();
            if (dto.parameters == null) {
                continue;
            }

            // Added and deleted parameters are represented as fixed size array
            // Index 0 ... The UID of the parameter
            // Index 1 ... The old / new value
            if (matchesAppParams(dto.parameters.added, filter)) {
                return true;
            }
            if (matchesAppParams(dto.parameters.deleted, filter)) {
                return true;
            }

            // Changed parameters are represented as map
            // Key of the map is the the UID of the parameter
            // The value is a fixed size array
            // Index 0 ... The old value
            // Index 1 ... The new value
            if (matches(dto.parameters.changed.keySet(), filter)) {
                return true;
            }
        }

        // no match
        return false;
    }

    private boolean matchesAppParams(Collection<String[]> values, String filter) {
        for (String[] entry : values) {
            String paramUid = entry[0];
            if (contains(paramUid, filter)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(Collection<String> values, String filter) {
        for (String value : values) {
            if (contains(value, filter)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(String value, String filter) {
        return value.toLowerCase().contains(filter.toLowerCase());
    }

}
