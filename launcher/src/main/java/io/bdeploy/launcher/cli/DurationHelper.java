package io.bdeploy.launcher.cli;

import java.time.Duration;

public class DurationHelper {

    private DurationHelper() {
    }

    public static String formatDuration(long timeInMillis) {
        Duration duration = Duration.ofMillis(timeInMillis);
        return String.format("%02d min %02d sec", duration.toMinutesPart(), duration.toSecondsPart());
    }

}
