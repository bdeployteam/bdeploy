package io.bdeploy.gradle.config;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

/**
 * Configures a source or target BDeploy server.
 */
public class BDeployRepositoryServerConfig {

	private String uri;
	private String token;

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
	
}
