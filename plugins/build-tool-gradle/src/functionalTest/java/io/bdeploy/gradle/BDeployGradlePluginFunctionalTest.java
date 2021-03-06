/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package io.bdeploy.gradle;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.Test;

/**
 * A simple functional test for the 'io.bdeploy.gradle.plugin' plugin.
 */
public class BDeployGradlePluginFunctionalTest {
	@Test
	public void noSource() throws IOException {
		File projectDir = new File("build/functionalTest");
		Files.createDirectories(projectDir.toPath());
		writeString(new File(projectDir, "settings.gradle"), "");
		writeString(new File(projectDir, "product-info.yaml"), "name: Test Product", "product: io.bdeploy/test",
				"vendor: BDeploy", "applications:", "  - test", "versionFile: product-version.yaml");

		File appInfo = new File(projectDir, "app/app-info.yaml");
		Files.createDirectories(appInfo.getParentFile().toPath());
		writeString(appInfo, "name: Test Application");

		writeString(new File(projectDir, "build.gradle"), "plugins {", "  id('io.bdeploy.gradle.plugin')", "}",
				"version = '1.2.3'",

				"task testTask(type: io.bdeploy.gradle.BDeployProductTask) {", "  dryRun = true",
				"  repositoryServer {", "    uri = 'https://localhost:7701/api'",
				"    token = project.getProperty('token')", "  }", "  product {", "    version = '2.3.4'",
				"    productInfo = file('product-info.yaml')", "    applications {", "      test {",
				"        os.add('WINDOWS')", "        os.add('LINUX')", "        yaml = file('app/app-info.yaml')",
				"      }", "    }", "    labels.put('this', 'label')", "  }", "}");

		BuildResult result = run(projectDir, "testTask", "--info", "-Ptoken=ABCDEFG");
		assertTrue(result.getOutput().contains("io.bdeploy/test"));
		assertTrue(result.getOutput().contains("2.3.4"));
	}

	private void writeString(File file, String... lines) throws IOException {
		try (Writer writer = new FileWriter(file)) {
			for (String line : lines) {
				writer.write(line);
				writer.write('\n');
			}
		}
	}

	private BuildResult run(File dir, String... arguments) {
		GradleRunner runner = GradleRunner.create();
		runner.forwardOutput();
		runner.withPluginClasspath();
		runner.withArguments(arguments);
		runner.withProjectDir(dir);
		return runner.build();
	}
}
