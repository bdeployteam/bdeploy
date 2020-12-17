package io.bdeploy.api.plugin.v1;

/**
 * Describes a mapping of a static path inside the JAR file of a plugin to a URL path in the plugins scope under which this path
 * should be made available on the server.
 */
public class PluginAssets {

    private final String jarPath;
    private final String urlPath;

    /**
     * @param jarPath the path inside the JAR file to make available.
     * @param urlPath the additional context path under which to make this asset available. Note that this is appended to a
     *            plugin-specific unique context-path, so it is not the full path on the server.
     */
    public PluginAssets(String jarPath, String urlPath) {
        this.jarPath = jarPath;
        this.urlPath = urlPath;
    }

    /**
     * @return the path inside the plugin's JAR file which is used to load resources when requested.
     */
    public String getJarPath() {
        return jarPath;
    }

    /**
     * @return the path inside the plugins namespace on the server under which this asset path should be hosted.
     */
    public String getUrlPath() {
        return urlPath;
    }

}
