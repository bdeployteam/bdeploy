package io.bdeploy.interfaces.configuration.pcu;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO provided by the PCU with details provided by the native {@Code ProcessHandle}
 */
public class ProcessHandleDto {

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
    public final List<ProcessHandleDto> children = new ArrayList<>();

}
