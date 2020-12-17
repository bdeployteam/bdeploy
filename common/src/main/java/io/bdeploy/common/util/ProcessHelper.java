package io.bdeploy.common.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ProcessHelper {

    private ProcessHelper() {
    }

    /**
     * Launches the given process as described by the given builder and waits for termination. The output that is written
     * to the standard out as well as standard error is returned as string.
     * <p>
     * <b>Implementation note:</b> Only suitable for short running processes that print out some details and terminate within
     * seconds.
     * </p>
     *
     * @param builder the process to launch
     * @return the output of the process
     */
    public static String launch(ProcessBuilder builder) {
        try {
            builder.redirectErrorStream(true);
            return readOutput(builder.start());
        } catch (IOException ioe) {
            return null;
        }
    }

    /** Reads the output of the given process and returns it as plain string */
    public static String readOutput(Process process) throws IOException {
        StringBuilder result = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line = null;
            while ((line = in.readLine()) != null) {
                result.append(line).append("\n");
            }
        }
        return result.toString();
    }

}
