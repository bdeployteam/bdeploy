package io.bdeploy.interfaces.configuration.pcu;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.base.Joiner;

/**
 * Contains detailed information about a given process and its children.
 */
public class ProcessDetailDto {

    /** Identifier of the process */
    public long pid;

    /** Start time of the process */
    public long startTime = -1;

    /** User that launched this process */
    public String user;

    /** Command-line used to launch this process */
    public String command;

    /** Command-line used to launch this process */
    public String[] arguments;

    /** Total CPU time accumulated of the process. */
    public long totalCpuDuration = -1;

    /** Information about the child processes of this process */
    public final List<ProcessDetailDto> children = new ArrayList<>();

    @Override
    public String toString() {
        return Joiner.on("\n").join(log());
    }

    /**
     * Returns a human readable string of the process details.
     */
    public List<String> log() {
        List<String> log = new ArrayList<>();
        log.add("ProcessDetail [ PID=" + pid + " ]");
        if (user != null) {
            log.add("Started by:  " + user);
        }
        if (startTime != -1) {
            SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
            log.add("Started at:  " + format.format(startTime));
        }
        if (command != null) {
            log.add("Command Line: " + command);
        }
        if (arguments != null) {
            log.add("Arguments: " + Arrays.toString(arguments));
        }
        if (!children.isEmpty()) {
            log.add("Children:");
            for (ProcessDetailDto child : children) {
                child.log().forEach(l -> log.add("\t" + l));
                log.add("\t---");
            }
        }
        return log;
    }

}
