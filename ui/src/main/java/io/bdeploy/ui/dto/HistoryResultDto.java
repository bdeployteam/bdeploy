package io.bdeploy.ui.dto;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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
    public void addAll(Iterable<HistoryEntryDto> dtos, HistoryFilterDto filter) {
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

    private static boolean matches(HistoryEntryDto dto, HistoryFilterDto filter) {
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

        // Match date/time - the format is hardcoded in the frontend as well.
        if (dto.timestamp > 0) {
            String formatted = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm:ss")
                    .format(Instant.ofEpochMilli(dto.timestamp).atZone(ZoneId.systemDefault()));
            if (contains(formatted, text)) {
                return true;
            }
        }

        // No match found
        return false;
    }

    private static boolean contains(String value, String filter) {
        return value.toLowerCase().contains(filter.toLowerCase());
    }

}
