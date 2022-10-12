package io.bdeploy.pcu;

import java.lang.ProcessHandle.Info;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.bdeploy.common.util.MdcLogger;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.interfaces.configuration.pcu.ProcessHandleDto;

/**
 * Contains helpers for the {@linkplain ProcessController}
 */
class ProcessControllerHelper {

    private ProcessControllerHelper() {
    }

    /**
     * Formats the given duration into a human readable string.
     *
     * <pre>
    *       3 days 4 hours 1 minute
    *       5 minutes 2 seconds
    *       5 seconds 360 milliseconds
     * </pre>
     *
     * @param value
     *            the value to format
     * @return the formatted value
     */
    public static String formatDuration(Duration value) {
        List<String> parts = new ArrayList<>();
        long days = value.toDaysPart();
        if (days == 1) {
            parts.add(days + " day");
        } else if (days > 1) {
            parts.add(days + " days");
        }
        long hours = value.toHoursPart();
        if (hours == 1) {
            parts.add(hours + " hour");
        } else if (hours > 1) {
            parts.add(hours + " hours");
        }
        long minutes = value.toMinutesPart();
        if (minutes == 1) {
            parts.add(minutes + " minute");
        } else if (minutes > 1) {
            parts.add(minutes + " minutes");
        }

        // Skip seconds if we have days, hours
        if (days > 0 || hours > 0) {
            return String.join(" ", parts);
        }
        long seconds = value.toSecondsPart();
        if (seconds == 1) {
            parts.add(seconds + " second");
        } else if (seconds > 1) {
            parts.add(seconds + " seconds");
        }

        // Skip milliseconds if we have minutes
        if (minutes > 0) {
            return String.join(" ", parts);
        }
        long millis = value.toMillisPart();
        if (millis == 1) {
            parts.add(millis + " millisecond");
        } else if (millis > 1 || parts.isEmpty()) {
            parts.add(millis + " milliseconds");
        }
        return String.join(" ", parts);
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

    /**
     * Returns the exit code or {@code null}
     */
    public static Integer getExitCode(Process process) {
        if (process != null) {
            return process.exitValue();
        }
        return null;
    }

    /**
     * The JVM on linux uses an invalid formula to calculate the absolute timestamp at which a process was started.
     * There are various dynamically updated offsets and timestamps in play, thus the value may change over time. There /is/ a
     * stable value we can use, and actually what we do here is the very same thing as the JVM does when reading the start time.
     * The read value is not absolute though, but relative to the kernel boot time (which is sufficient for what we want). We just
     * omit addition of real time and boot time offsets as done in the linux kernel when querying the boottime of the kernel.
     */
    public static long getProcessStartTimestampCorrected(MdcLogger logger, ProcessHandle handle, Instant startTime) {
        // we (for now) trust the OS to deliver a stable absolute timestamp.
        if (OsHelper.getRunningOs() != OperatingSystem.LINUX) {
            return startTime.toEpochMilli();
        }

        try {
            if (!handle.isAlive()) {
                logger.log(l -> l.warn("Cannot read corrected start time, process no longer alive, PID = {}", handle.pid()));
                return startTime.toEpochMilli();
            }

            // read the single line from /proc/[pid]/stat, field no 22 is the start time.
            Path path = Paths.get("/proc", String.valueOf(handle.pid()), "stat");

            if (!Files.exists(path)) {
                logger.log(l -> l.warn("Cannot read corrected start time, stat file does not exist, PID = {}", handle.pid()));
                return startTime.toEpochMilli();
            }

            String line = Files.readString(path);
            String[] split = line.split(" ");
            return Long.parseLong(split[21]);
        } catch (Exception e) {
            logger.log(l -> l.warn("Cannot read corrected start time of process, PID = {}.", handle.pid(), e));
            return startTime.toEpochMilli();
        }
    }

}
