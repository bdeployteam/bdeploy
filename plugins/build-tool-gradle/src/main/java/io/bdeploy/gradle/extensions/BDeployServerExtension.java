package io.bdeploy.gradle.extensions;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.model.ObjectFactory;

public class BDeployServerExtension {

	private final NamedDomainObjectContainer<ServerExtension> servers;
	
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
