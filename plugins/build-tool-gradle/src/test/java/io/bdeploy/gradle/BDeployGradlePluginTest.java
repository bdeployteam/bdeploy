/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package io.bdeploy.gradle;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Test;

/**
 * A simple unit test for the 'io.bdeploy.gradle.greeting' plugin.
 */
public class BDeployGradlePluginTest {

	@Test
	public void pluginApplication() {
		Project root = ProjectBuilder.builder().build();

		root.getPlugins().apply(BDeployGradlePlugin.PLUGIN_ID);
	}

}
