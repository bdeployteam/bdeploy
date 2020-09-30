package io.bdeploy.gradle;

import javax.ws.rs.core.UriBuilder;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.gradle.config.BDeployServerConfig;

/**
 * Pushes a previously built product to a specified server.
 */
public class BDeployPushTask extends DefaultTask {

	private BDeployProductTask productTask;
	private DirectoryProperty localBHive;
	private Property<Key> key;
	private BDeployServerConfig target = new BDeployServerConfig();
	
	public BDeployPushTask() {
		localBHive = getProject().getObjects().directoryProperty();
		key = getProject().getObjects().property(Key.class);
		
		getProject().afterEvaluate(prj -> {
			if(productTask != null) {
				if(!localBHive.isPresent()) {
					localBHive.set(productTask.getLocalBHive());
				}
				if(!key.isPresent()) {
					key.set(prj.provider(() -> productTask.getKey()));
				}
			}
		});
	}
	
	@TaskAction
	public void perform() {
		if(target.getUri() == null || target.getToken() == null || target.getInstanceGroup() == null) {
			throw new IllegalStateException("Set 'target.uri', 'target.token' and 'target.instanceGroup'");
		}
		
		RemoteService svc = new RemoteService(UriBuilder.fromUri(target.getUri()).build(), target.getToken());
		
		System.out.println(" >> Pushing " + key.get() + " to " + svc.getUri());
		
		ActivityReporter reporter = new ActivityReporter.Null();
    	try(BHive local = new BHive(localBHive.getAsFile().get().toURI(), reporter)) {
    		local.execute(new PushOperation().setRemote(svc).setHiveName(target.getInstanceGroup()).addManifest(key.get()));
    	}
	}
	
	/**
	 * @return the target server to push to.
	 */
	@Nested
	public BDeployServerConfig getTarget() {
		return target;
	}
	
	public void target(Action<? super BDeployServerConfig> action) {
		action.execute(target);
	}
	
	/**
	 * @param task the {@link BDeployProductTask} to grab configuration from (local BHive path, key to push).
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
