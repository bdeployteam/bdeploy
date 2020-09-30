package io.bdeploy.gradle.extensions;

import java.io.File;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;

/**
 * Configures a single application.
 */
public class ApplicationExtension {

	private final String name;
	private final RegularFileProperty yaml;
	private final ListProperty<String> os;
	
	public ApplicationExtension(String name, ObjectFactory factory) {
		this.name = name;
		this.yaml = factory.fileProperty();
		this.os = factory.listProperty(String.class);
	}
	
	/**
	 * @return the name of the application as specified in the 'product-info.yaml'
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * @return the path to the 'app-info.yaml'. The 'app-info.yaml' is assumed to be placed in the
	 *         root directory of the binaries for the specified application.
	 */
	public RegularFileProperty getYaml() {
		return yaml;
	}
	
	public void setYaml(File file) {
		yaml.set(file);
	}
	
	/**
	 * @return the list of supported operating systems. if not given, the list is inferred from the 'app-info.yaml'
	 */
	public ListProperty<String> getOs() {
		return os;
	}
	
}
