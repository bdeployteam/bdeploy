package io.bdeploy.gradle;

import java.net.URI;

import javax.ws.rs.core.UriBuilder;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.security.RemoteService;

/**
 * Packages a product as a ZIP file.
 */
public class BDeployZipTask extends DefaultTask {
	
	private BDeployProductTask productTask;
	private DirectoryProperty localBHive;
	private Property<Key> key;
	private RegularFileProperty output;
	
	public BDeployZipTask() {
		localBHive = getProject().getObjects().directoryProperty();
		key = getProject().getObjects().property(Key.class);
		output = getProject().getObjects().fileProperty();
		
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
		System.out.println(" >> Zip'ing " + key.get());
		
		ActivityReporter reporter = new ActivityReporter.Null();
		URI targetUri = UriBuilder.fromUri("jar:" + output.getAsFile().get().toURI()).build();
    	try(BHive local = new BHive(localBHive.getAsFile().get().toURI(), reporter)) {
    		local.execute(new PushOperation().setRemote(new RemoteService(targetUri)).addManifest(key.get()));
    	}
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
