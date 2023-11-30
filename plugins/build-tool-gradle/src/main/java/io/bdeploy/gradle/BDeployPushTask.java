package io.bdeploy.gradle;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.gradle.extensions.BDeployServerExtension;
import io.bdeploy.gradle.extensions.ServerExtension;

/**
 * Pushes a previously built product to a specified server.
 */
public class BDeployPushTask extends DefaultTask {


	private static final Logger log = LoggerFactory.getLogger(BDeployPushTask.class);

	private BDeployProductTask productTask;
	private DirectoryProperty localBHive;
	private Property<Key> key;

	/**
	 * @param factory the factory to create properties
	 */
	@Inject
	public BDeployPushTask(ObjectFactory factory) {
		localBHive = factory.directoryProperty();
		key = factory.property(Key.class);
		getExtensions().create("target", BDeployServerExtension.class, factory);

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
		BDeployServerExtension ext = getExtensions().getByType(BDeployServerExtension.class);
		if (ext.getServers().isEmpty()) {
			throw new IllegalStateException("No server configured");
		}

		ActivityReporter reporter = getProject().hasProperty("verbose") ? new ActivityReporter.Stream(System.out)
				: new ActivityReporter.Null();
		for (ServerExtension target : ext.getServers().getAsMap().values()) {
			RemoteService svc = target.getRemote();

			System.out.println(" >> Pushing " + key.get() + " to " + target.getName());

			try (BHive local = new BHive(localBHive.getAsFile().get().toURI(), null, reporter)) {
				local.execute(new PushOperation().setRemote(svc).setHiveName(target.getInstanceGroup().get())
						.addManifest(key.get()));
			} catch (IllegalStateException ise) {
				Throwable e =ise;
				while (e.getCause() != null){
					e = e.getCause();
				}
				log.error("Cannot push to target: {}", e.getMessage());
				throw ise;
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
	 * @return the product to push.
	 */
	@Input
	public Property<Key> getKey() {
		return key;
	}

}
