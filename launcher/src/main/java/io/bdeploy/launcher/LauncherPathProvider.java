package io.bdeploy.launcher;

import java.nio.file.Path;

import io.bdeploy.interfaces.variables.DeploymentPathProvider;

/**
 * Handles references to special directories in the local BDEPLOY_HOME.
 *
 * @see SpecialDirectory
 */
public class LauncherPathProvider {

    /**
     * Each {@link SpecialDirectory} represents a {@link Path} to a concrete directory of a BDeploy installation.
     */
    public enum SpecialDirectory {

        /** <i>[BDEPLOY_HOME]</i> */
        HOME(null),

        /** <i>[BDEPLOY_HOME]/apps</i> */
        APPS("apps"),
        /** <i>[BDEPLOY_HOME]/bhive</i> */
        BHIVE("bhive"),
        /** <i>[BDEPLOY_HOME]/graveyard</i> */
        GRAVEYARD("graveyard"),
        /** <i>[BDEPLOY_HOME]/launcher</i> */
        LAUNCHER("launcher"),
        /** <i>[BDEPLOY_HOME]/logs</i> */
        LOGS("logs"),

        /** <i>[BDEPLOY_HOME]/apps/pool</i> */
        MANIFEST_POOL("pool"),
        /** <i>[BDEPLOY_HOME]/apps/start_scripts</i> */
        START_SCRIPTS("start_scripts"),
        /** <i>[BDEPLOY_HOME]/apps/file_assoc_scripts</i> */
        FILE_ASSOC_SCRIPTS("file_assoc_scripts"),

        /** <i>[BDEPLOY_HOME]/apps/[applicationId]</i> */
        APP(null),
        /** <i>[BDEPLOY_HOME]/apps/[applicationId]/bin/{@value LauncherPathProvider#DEFAULT_TAG}</i> */
        APP_BIN_TAG("bin"),
        /** <i>[BDEPLOY_HOME]/apps/[applicationId]/bin/{@value LauncherPathProvider#DEFAULT_TAG}/config</i> */
        CONFIG("config"),

        /** <i>[BDEPLOY_HOME]/launcher/bin</i> */
        LAUNCHER_BIN("bin");

        private final String dirName;

        private SpecialDirectory(String dirName) {
            this.dirName = dirName;
        }

        public String getDirName() {
            if (dirName == null) {
                throw new UnsupportedOperationException("Special directory " + name() + " does not have a fixed name.");
            }
            return dirName;
        }
    }

    private static final String DEFAULT_TAG = "1";
    private final Path homeDir;
    private String presetApplicationId;

    /**
     * @param homeDir the BDEPLOY_HOME directory
     */
    public LauncherPathProvider(Path homeDir) {
        this.homeDir = homeDir;
    }

    /**
     * Allows the {@link LauncherPathProvider} to be set to specific client application which it will remember. This client
     * application will be used for future application-sensitive navigations.
     *
     * @param applicationId the ID of the client application to remember
     * @return <code>this</code>, for chaining convenience
     */
    public LauncherPathProvider setApplicationId(String applicationId) {
        this.presetApplicationId = applicationId;
        return this;
    }

    /**
     * Calculates the {@link Path} to the specified {@link SpecialDirectory}. Throws an {@link UnsupportedOperationException} when
     * attempting to navigate to a directory that requires an application ID to resolve.
     *
     * @param dir the {@link SpecialDirectory} to look up
     * @return the {@link Path} to the {@link SpecialDirectory} in its {@link Path#normalize() normalized} and
     *         {@link Path#toAbsolutePath() absolute} form
     * @see #get(SpecialDirectory, String)
     */
    public Path get(SpecialDirectory dir) {
        return get(dir, presetApplicationId);
    }

    /**
     * Calculates the {@link Path} to the specified {@link SpecialDirectory}.
     *
     * @param dir the {@link SpecialDirectory} to look up
     * @param applicationId the ID of the concrete instance to navigate to
     * @return the {@link Path} to the {@link SpecialDirectory} in its {@link Path#normalize() normalized} and
     *         {@link Path#toAbsolutePath() absolute} form
     * @see #get(SpecialDirectory)
     */
    public Path get(SpecialDirectory dir, String applicationId) {
        Path result = switch (dir) {
            case HOME -> homeDir;
            case APPS, BHIVE, GRAVEYARD, LAUNCHER, LOGS -> get(SpecialDirectory.HOME, applicationId).resolve(dir.getDirName());
            case MANIFEST_POOL, START_SCRIPTS, FILE_ASSOC_SCRIPTS -> get(SpecialDirectory.APPS, applicationId)
                    .resolve(dir.getDirName());
            case APP -> get(SpecialDirectory.APPS, applicationId).resolve(applicationId);
            case APP_BIN_TAG -> get(SpecialDirectory.APP, applicationId).resolve(dir.getDirName()).resolve(DEFAULT_TAG);
            case CONFIG -> get(SpecialDirectory.APP_BIN_TAG, applicationId).resolve(dir.getDirName());
            case LAUNCHER_BIN -> get(SpecialDirectory.LAUNCHER, applicationId).resolve(dir.getDirName());
            default -> throw new IllegalArgumentException("Unknown special directory: " + dir);
        };
        return result.normalize().toAbsolutePath();
    }

    /**
     * Transforms this {@link LauncherPathProvider} into a {@link DeploymentPathProvider} which is based on the same client
     * application.
     * <p>
     * The logDataDir is always set to <code>null</code> and the tag is always set to {@value #DEFAULT_TAG}.
     *
     * @see #toDeploymentPathProvider(String)
     */
    public DeploymentPathProvider toDeploymentPathProvider() {
        return toDeploymentPathProvider(presetApplicationId);
    }

    /**
     * Transforms this {@link LauncherPathProvider} into a {@link DeploymentPathProvider} which is based on the client
     * application that is defined by the given application ID.
     * <p>
     * The logDataDir is always set to <code>null</code> and the tag is always set to {@value #DEFAULT_TAG}.
     *
     * @param applicationId the ID of the client application that the {@link DeploymentPathProvider} will be based on
     * @see #toDeploymentPathProvider()
     */
    public DeploymentPathProvider toDeploymentPathProvider(String applicationId) {
        if (applicationId == null) {
            throw new IllegalArgumentException("An instance ID must be provided.");
        }
        return new DeploymentPathProvider(get(SpecialDirectory.APPS), null, applicationId, DEFAULT_TAG);
    }
}
