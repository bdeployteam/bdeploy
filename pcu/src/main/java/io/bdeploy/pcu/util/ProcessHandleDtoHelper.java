package io.bdeploy.pcu.util;

import java.lang.ProcessHandle.Info;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import io.bdeploy.interfaces.configuration.pcu.ProcessHandleDto;

public class ProcessHandleDtoHelper {

    private ProcessHandleDtoHelper() {
    }

    /**
     * Recursively collects runtime information about the given process.
     *
     * @param process
     *            the handle to get the details from
     * @return the details
     */
    public static ProcessHandleDto collectProcessInfo(ProcessHandle process) {
        ProcessHandleDto dto = new ProcessHandleDto();
        dto.pid = process.pid();

        // Collect process info
        Info info = process.info();
        Optional<Instant> startInstant = info.startInstant();
        if (startInstant.isPresent()) {
            dto.startTime = startInstant.get().toEpochMilli();
        }
        Optional<Duration> totalCpuDuration = info.totalCpuDuration();
        if (totalCpuDuration.isPresent()) {
            dto.totalCpuDuration = totalCpuDuration.get().getSeconds();
        }
        dto.command = info.command().orElse(null);
        dto.arguments = info.arguments().orElse(null);
        dto.user = info.user().orElse(null);

        // Collect child info
        process.children().forEach(child -> dto.children.add(collectProcessInfo(child)));
        return dto;
    }

}
