package io.bdeploy.gradle.config;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.jersey.cli.LocalLoginManager;
import jakarta.ws.rs.core.UriBuilder;

/**
 * Configures a source or target BDeploy server.
 */
public class BDeployRepositoryServerConfig {

	private String uri;
	private String token;
	private Boolean useLogin;
	private String login;

	/**
	 * @return the server uri, must come in pair with token
	 */
	@Optional
	@Input
	public String getUri() {
		return uri;
	}
	
	/**
	 * @param uri the server uri, must come in pair with token
	 */
	public void setUri(String uri) {
		this.uri = uri;
	}

	/**
	 * @return the auth token, must come in pair with uri
	 */
	@Optional
	@Input
	public String getToken() {
		return token;
	}
	
	/**
	 * @param token the auth token, must come in pair with uri
	 */
	public void setToken(String token) {
		this.token = token;
	}
	
	/**
	 * @return whether to use local login
	 */
	@Optional
	@Input
	public Boolean isUseLogin() {
		return useLogin;
	}
	
	/**
	 * @param useLogin whether to use local login
	 */
	public void setUseLogin(Boolean useLogin) {
		this.useLogin = useLogin;
	}
	
	/**
	 * @return the login to use, if null and useLogin, uses the active login
	 */
	@Optional
	@Input
	public String getLogin() {
		return login;
	}
	
	/**
	 * @param login the login to use, if null and useLogin, uses the active login
	 */
	public void setLogin(String login) {
		this.login = login;
	}
	
	/**
	 * @return whether this repository server is sufficiently configured. 
	 */
	@Internal
	public boolean isConfigured() {
		if(Boolean.TRUE.equals(useLogin)) {
			LocalLoginManager llm = new LocalLoginManager();
			return (login == null && llm.getCurrentService() != null) || (login != null && llm.getNamedService(login) != null);
		}
		return (uri != null && token != null);
	}
	
	/**
	 * @return the configured remote service.
	 */
	@Internal
	public RemoteService getRemote() {
		if (!isConfigured()) {
			throw new IllegalArgumentException("Need a configured server");
		}
		
		if(Boolean.TRUE.equals(useLogin)) {
			LocalLoginManager llm = new LocalLoginManager();
			if(login == null) {
			return llm.getCurrentService();
			} else {
				return llm.getNamedService(login);
			}
		}
		
		return new RemoteService(UriBuilder.fromUri(uri).build(), token);
	}

}
