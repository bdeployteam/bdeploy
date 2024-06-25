package io.bdeploy.launcher.cli.scripts;

public enum LaunchMode {

    /**
     * The client application was launched via a start script that is available in the PATH environment variable.
     */
    PATH,

    /**
     * The client application was launched via file association.
     */
    ASSOCIATION;

    public static final String LAUNCH_MODE_ENV_VAR_NAME = "BDEPLOY_LAUNCH_MODE";
}
