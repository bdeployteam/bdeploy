package io.bdeploy.bhive.op;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;

import java.nio.file.Path;

import io.bdeploy.bhive.BHive;

public abstract class DirectoryLockModificationOperation extends BHive.Operation<Void> {

    protected static final int RETRIES = 100_000;
    protected static final int SLEEP_MILLIS = 10;
    protected Path directory;

    @Override
    public Void call() throws Exception {
        assertNotNull(directory, "No directory to lock.");
        doCall(directory.resolve(".lock"));
        return null;
    }

    /**
     * Sets the directory that should be locked.
     */
    public DirectoryLockModificationOperation setDirectory(Path directory) {
        this.directory = directory;
        return this;
    }

    abstract protected void doCall(Path lockFile) throws Exception;
}
