/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package io.bdeploy.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * The BDeploy Gradle Plugin.
 * <p>
 * The Plugin provides Tasks to build, package and push products from arbitrary applications. No tasks are created by default.
 */
public class BDeployGradlePlugin implements Plugin<Project> {

    /**
     * The plugin's id as used to apply in build.gradle
     */
    public static final String PLUGIN_ID = "io.bdeploy.gradle.plugin";

    /**
     * Apply the plugin to the given project.
     */
    public void apply(Project project) {
        // nothing to do when applying the plugin.
    }
}
