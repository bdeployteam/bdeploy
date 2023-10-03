package io.bdeploy.bhive;

import io.bdeploy.bhive.BHive.Operation;

/**
 * Interface for objects accepting a {@link Operation} for execution.
 * <p>
 * This can be either a {@link BHive} itself, or an object bound to a specific {@link BHive}, for instance an {@link Operation}.
 */
public interface BHiveExecution {

    /**
     * @param op the {@link Operation} to execute
     * @return the {@link Operation}s declared return type.
     */
    public <X> X execute(BHive.Operation<X> op);

    /**
     * @return the transactions manager for this {@link BHiveExecution}
     */
    public BHiveTransactions getTransactions();

    /**
     * @return an object which can be used to synchronize for a certain id/name on this BHive
     */
    public Object getSynchronizationObject(String name);

}