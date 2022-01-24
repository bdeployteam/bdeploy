package io.bdeploy.interfaces.configuration.pcu;

import java.util.List;

/**
 * DTO provided by the PCU with detailed information about a given process and its children.
 */
public class ProcessDetailDto {

    /** Time when the process stopped / crashed */
    public long stopTime = -1;

    /** Time when we are going to restart the application */
    public long recoverAt;

    /** Duration in seconds that we are waiting before re-launching */
    public long recoverDelay;

    /** Number of retry attempts */
    public long retryCount;

    /** Total number of retry attempts that are executed */
    public long maxRetryCount;

    /** True if the stdin stream is available/open for writing */
    public boolean hasStdin;

    /** Status and instance information */
    public ProcessStatusDto status;

    /** Details about the process and its children */
    public ProcessHandleDto handle;

    /** Details about the last probes against the process */
    public List<ProcessProbeResultDto> lastProbes;

}
