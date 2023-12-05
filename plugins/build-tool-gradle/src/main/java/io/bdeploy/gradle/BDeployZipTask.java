package io.bdeploy.gradle;

import java.net.URI;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.security.RemoteService;
import jakarta.ws.rs.core.UriBuilder;

/**
 * Packages a product as a ZIP file.
 */
public class BDeployZipTask extends DefaultTask {

	private static final Logger log = LoggerFactory.getLogger(BDeployZipTask.class);
	
	private BDeployProductTask productTask;
	private DirectoryProperty localBHive;
	private Property<Key> key;
	private RegularFileProperty output;

	/**
	 * @param factory the factory to create properties.
	 */
	@Inject
	public BDeployZipTask(ObjectFactory factory) {
		localBHive = factory.directoryProperty();
		key = factory.property(Key.class);
		output = factory.fileProperty();

		getProject().afterEvaluate(prj -> {
			if (productTask != null) {
				if (!localBHive.isPresent()) {
					localBHive.set(productTask.getLocalBHive());
				}
				if (!key.isPresent()) {
					key.set(prj.provider(() -> productTask.getKey()));
				}
			}
		});
	}

	/**
	 * Executes the task
	 */
	@TaskAction
	public void perform() {
		log.warn(" >> Zip'ing " + key.get());

		ActivityReporter reporter = getProject().hasProperty("verbose") ? new ActivityReporter.Stream(System.out)
				: new ActivityReporter.Null();
		URI targetUri = UriBuilder.fromUri("jar:" + output.getAsFile().get().toURI()).build();
		try (BHive local = new BHive(localBHive.getAsFile().get().toURI(), null, reporter)) {
			local.execute(new PushOperation().setRemote(new RemoteService(targetUri)).addManifest(key.get()));
		} catch(Exception e) {
			log.error("Cannot create zip: {}", e.toString());
			if(log.isInfoEnabled()) {
				log.info("Exception:", e);
			}
		}
	}

	/**
	 * @param task the {@link BDeployProductTask} to grab configuration from (local
	 *             BHive path, key to push).
	 */
	public void of(BDeployProductTask task) {
		productTask = task;
	}

	/**
	 * @return the local BHive containing the specified product.
	 */
	@InputDirectory
	public DirectoryProperty getLocalBHive() {
		return localBHive;
	}

	/**
	 * @return the product to package.
	 */
	@Input
	public Property<Key> getKey() {
		return key;
	}

	/**
	 * @return the target ZIP file.
	 */
	@OutputFile
	public RegularFileProperty getOutput() {
		return output;
	}

}
