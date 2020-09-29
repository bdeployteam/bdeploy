package io.bdeploy.gradle.extensions;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

/**
 * Defines a 'product'. Specifies version, path to 'product-info.yaml', all applications and all labels.
 */
public class BDeployProductExtension {

	private final Property<String> version;
	private final RegularFileProperty productInfo;
	private final NamedDomainObjectContainer<ApplicationExtension> applications;
	private final MapProperty<String, String> labels;
	
	public BDeployProductExtension(ObjectFactory factory) {
		this.version = factory.property(String.class);
		this.productInfo = factory.fileProperty();
		this.applications = factory.domainObjectContainer(ApplicationExtension.class, (name) -> {
			return new ApplicationExtension(name, factory);
		});
		this.labels = factory.mapProperty(String.class, String.class);
	}
	
	/**
	 * @return the path to the 'product-info.yaml'.
	 */
	public RegularFileProperty getProductInfo() {
		return productInfo;
	}
	
	/**
	 * @return the version of the product. If not given, the project version is used.
	 */
	public Property<String> getVersion() {
		return version;
	}
	
	/**
	 * @return the applications to be packaged with the product.
	 */
	public NamedDomainObjectContainer<ApplicationExtension> getApplications() {
		return applications;
	}
	
	/**
	 * @return the labels of the product, can contain arbitrary metadata.
	 */
	public MapProperty<String, String> getLabels() {
		return labels;
	}
	
}
