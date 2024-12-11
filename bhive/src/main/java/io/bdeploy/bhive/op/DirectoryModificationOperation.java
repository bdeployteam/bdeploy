package io.bdeploy.bhive.op;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;

import java.nio.file.Path;

import io.bdeploy.bhive.BHive;

public abstract class DirectoryModificationOperation<T> extends BHive.Operation<T> {

    protected static final int RETRIES = 100_000;
    protected static final int SLEEP_MILLIS = 10;
    protected Path directory;

    @Override
    public T call() {
        assertNotNull(directory, "No directory to lock.");
        return doCall(directory.resolve(".lock"));
    }

    /**
     * Sets the directory that should be locked.
     */
    public DirectoryModificationOperation<T> setDirectory(Path directory) {
        this.directory = directory;
        return this;
    }

    protected abstract T doCall(Path lockFile);
}
