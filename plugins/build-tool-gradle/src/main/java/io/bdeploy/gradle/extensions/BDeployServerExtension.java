package io.bdeploy.gradle.extensions;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.model.ObjectFactory;

/**
 * Collects multiple target servers
 */
public class BDeployServerExtension {

	private final NamedDomainObjectContainer<ServerExtension> servers;

	/**
	 * @param factory the factory to create properties with.
	 */
	public BDeployServerExtension(ObjectFactory factory) {
		this.servers = factory.domainObjectContainer(ServerExtension.class, (name) -> {
			return new ServerExtension(name, factory);
		});
	}

	/**
	 * @return all configured servers
	 */
	public NamedDomainObjectContainer<ServerExtension> getServers() {
		return servers;
	}

}
