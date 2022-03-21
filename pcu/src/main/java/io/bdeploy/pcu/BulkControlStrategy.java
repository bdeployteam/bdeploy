package io.bdeploy.pcu;

import java.util.Collection;
import java.util.List;

import io.bdeploy.interfaces.configuration.pcu.ProcessControlGroupConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlGroupConfiguration.ProcessControlGroupHandlingType;

/**
 * Implementors reflect the differences in handling of {@link ProcessControlGroupHandlingType}s.
 */
public interface BulkControlStrategy extends AutoCloseable {

    public static BulkControlStrategy create(String user, String instance, String tag, ProcessControlGroupConfiguration group,
            ProcessList processes, ProcessControlGroupHandlingType type) {
        if (type == ProcessControlGroupHandlingType.PARALLEL) {
            return new ParallelBulkControl(user, instance, tag, group, processes);
        } else {
            return new SequentialBulkControl(user, instance, tag, group, processes);
        }
    }

    /**
     * Start all processes of the control group.
     *
     * @param toStart all processes which should be started using the groups configuration.
     * @return a {@link List} of process IDs which could <strong>not</strong> be started.
     */
    public List<String> startGroup(Collection<String> toStart);

    /**
     * Stop all processes of the control group.
     *
     * @param toStop all processes which should be stopped using the groups configuration.
     * @return a {@link List} of process IDs which were stopped.
     */
    public List<String> stopGroup(Collection<ProcessController> toStop);

    /**
     * {@link AutoCloseable#close()} without throws.
     */
    @Override
    void close();
}
