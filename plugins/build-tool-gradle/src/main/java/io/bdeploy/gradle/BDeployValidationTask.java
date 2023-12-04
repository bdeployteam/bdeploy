package io.bdeploy.gradle;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.api.validation.v1.ProductValidationHelper;
import io.bdeploy.api.validation.v1.dto.ProductValidationIssueApi.ProductValidationSeverity;
import io.bdeploy.api.validation.v1.dto.ProductValidationResponseApi;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.gradle.config.BDeployRepositoryServerConfig;

/**
 * Validates a BDeploy product from a given product-validation.yaml
 */
public class BDeployValidationTask extends DefaultTask {

	private static final Logger log = LoggerFactory.getLogger(BDeployValidationTask.class);

	private BDeployRepositoryServerConfig validationServer = new BDeployRepositoryServerConfig();
	private RegularFileProperty validationYaml;

	/**
	 * @param factory creates the task
	 */
	@Inject
	public BDeployValidationTask(ObjectFactory factory) {
		validationYaml = factory.fileProperty();
		
		// never up to date.
		getOutputs().upToDateWhen(e -> false);
	}

	/**
	 * Executes the task
	 * 
	 * @throws IOException in case of issues.
	 */
	@TaskAction
	public void perform() throws IOException {
		// apply from extension if set, but prefer local configuration.
		File descriptorYaml = validationYaml.getAsFile().getOrNull();

		RemoteService sourceServer = validationServer.getRemote();

		if (descriptorYaml == null || !descriptorYaml.exists()) {
			throw new IllegalArgumentException("product-validation.yaml is not set or does not exist: " + descriptorYaml);
		}

		log.warn(" :: Repository Server: {}", sourceServer.getUri());
		log.warn(" :: Product Validation: {}", descriptorYaml);

		ProductValidationResponseApi validate = ProductValidationHelper.validate(descriptorYaml.toPath(), sourceServer);
		
		for(var issue : validate.issues) {
			log.error("{}: {}", issue.severity.name(), issue.message);
		}
		
		if(validate.issues.stream().anyMatch(i -> i.severity == ProductValidationSeverity.ERROR)) {
			throw new IllegalStateException("Errors found during validation");
		}
	}

	/**
	 * @return the server which is used to validate the product
	 */
	@Nested
	public BDeployRepositoryServerConfig getValidationServer() {
		return validationServer;
	}

	/**
	 * @param action configuration action for validation server.
	 */
	public void validationServer(Action<? super BDeployRepositoryServerConfig> action) {
		action.execute(validationServer);
	}

	/**
	 * @return path to the validation YAML file which is used.
	 */
	@InputFile
	public RegularFileProperty getValidationYaml() {
		return validationYaml;
	}
	
}
