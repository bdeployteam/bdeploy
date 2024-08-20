package io.bdeploy.interfaces.variables;

import java.nio.file.Path;

import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;

/**
 * Handles references to special paths in the deployment.
 *
 * @see SpecialDirectory
 */
public class DeploymentPathProvider {

    public enum SpecialDirectory {

        ROOT("root"),
        CONFIG("config"),
        RUNTIME("runtime"),
        BIN("bin"),
        DATA("data"),
        LOG_DATA("log_data"),
        MANIFEST_POOL("pool"),
        INSTANCE_MANIFEST_POOL("pool");

        private final String dirName;

        private SpecialDirectory(String dirName) {
            this.dirName = dirName;
        }

        public String getDirName() {
            return dirName;
        }
    }

    private final Path rootDir;
    private final Path logDataDir;
    private final String tagId;

    /**
     * @param deploymentDir The deployment directory which contains all instances
     * @param logDataDir The root directory for all log data
     * @param inm The {@link InstanceNodeManifest} that describes the concrete instance
     */
    public DeploymentPathProvider(Path deploymentDir, Path logDataDir, InstanceNodeManifest inm) {
        this(deploymentDir, logDataDir, inm.getId(), inm.getKey().getTag());
    }

    /**
     * @param deploymentDir The deployment directory which contains all instances
     * @param logDataDir The root directory for all log data
     * @param itemId The ID of the concrete item (e.g. the ID of the instance)
     * @param tagId The ID of the concrete installation of the instance in question
     */
    public DeploymentPathProvider(Path deploymentDir, Path logDataDir, String itemId, String tagId) {
        this.rootDir = deploymentDir.resolve(itemId);
        this.logDataDir = logDataDir == null ? null : logDataDir.resolve(itemId);
        this.tagId = tagId;
    }

    /**
     * @param dir the directory to get
     * @return the directory which is guaranteed to exist.
     * @see #get(SpecialDirectory)
     */
    public Path getAndCreate(SpecialDirectory dir) {
        Path p = get(dir);
        PathHelper.mkdirs(p);
        return p;
    }

    /**
     * @param dir the {@link SpecialDirectory} to look up.
     * @return the {@link Path} to the {@link SpecialDirectory}
     */
    public Path get(SpecialDirectory dir) {
        return switch (dir) {
            case ROOT -> rootDir;
            case DATA -> get(SpecialDirectory.ROOT).resolve(dir.dirName);
            case BIN -> get(SpecialDirectory.ROOT).resolve(dir.dirName).resolve(tagId);
            case MANIFEST_POOL -> get(SpecialDirectory.ROOT).getParent().resolve(dir.dirName); // Reaches outside of the given "root" deploymentDir - assumes that the parent is shared by all instances
            case INSTANCE_MANIFEST_POOL -> get(SpecialDirectory.ROOT).resolve(dir.dirName);
            case CONFIG, RUNTIME -> get(SpecialDirectory.BIN).resolve(dir.dirName);
            case LOG_DATA -> logDataDir != null ? logDataDir : get(SpecialDirectory.DATA); // Default path of logData is equal to DATA for compatibility with older versions
            default -> throw new IllegalArgumentException("Unhandled special directory: " + dir);
        };
    }
}
