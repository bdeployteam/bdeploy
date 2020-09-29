package io.bdeploy.gradle.config;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

/**
 * Configures a source or target BDeploy server.
 */
public class BDeployServerConfig {

	private String uri;
	private String token;
	private String instanceGroup;

	@Optional
	@Input
	public String getUri() {
		return uri;
	}
	
	public void setUri(String uri) {
		this.uri = uri;
	}
	
	@Optional
	@Input
	public String getToken() {
		return token;
	}
	
	public void setToken(String token) {
		this.token = token;
	}
	
	@Optional
	@Input
	public String getInstanceGroup() {
		return instanceGroup;
	}
	
	public void setInstanceGroup(String instanceGroup) {
		this.instanceGroup = instanceGroup;
	}
	
}
