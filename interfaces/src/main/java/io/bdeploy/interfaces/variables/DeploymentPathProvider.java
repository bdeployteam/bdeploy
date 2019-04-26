package io.bdeploy.interfaces.variables;

import java.nio.file.Path;

import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;

/**
 * Handles references to special paths in the deployment.
 *
 * @see SpecialDirectory
 */
public class DeploymentPathProvider {

    public enum SpecialDirectory {
        CONFIG("config"),
        RUNTIME("runtime"),
        BIN("bin"),
        DATA("data");

        private final String dirName;

        private SpecialDirectory(String dirName) {
            this.dirName = dirName;
        }
    }

    private final Path deploymentDir;
    private final String installationId;

    /**
     * @param deploymentDir the root directory for all installations/versions of a
     *            {@link InstanceNodeConfiguration}.
     * @param installationId the ID of the concrete installation in question.
     */
    public DeploymentPathProvider(Path deploymentDir, String installationId) {
        this.deploymentDir = deploymentDir;
        this.installationId = installationId;
    }

    /**
     * @param dir the {@link SpecialDirectory} to look up.
     * @return the {@link Path} to the {@link SpecialDirectory}
     */
    public Path get(SpecialDirectory dir) {
        switch (dir) {
            case DATA:
                return deploymentDir.resolve(dir.dirName);
            case BIN:
                return deploymentDir.resolve(dir.dirName).resolve(installationId);
            case CONFIG:
                return get(SpecialDirectory.BIN).resolve(dir.dirName);
            case RUNTIME:
                return get(SpecialDirectory.BIN).resolve(dir.dirName);
        }
        throw new IllegalArgumentException("Unhandled special directory: " + dir);
    }

    /**
     * @param dir the directory to get
     * @return the directory which is guaranteed to exist.
     * @see #get(SpecialDirectory)
     */
    public Path getAndCreate(SpecialDirectory dir) {
        return create(get(dir));
    }

    private Path create(Path p) {
        PathHelper.mkdirs(p);
        return p;
    }

}
