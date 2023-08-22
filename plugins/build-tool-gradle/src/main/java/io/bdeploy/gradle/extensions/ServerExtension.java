package io.bdeploy.gradle.extensions;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.StringHelper;
import io.bdeploy.jersey.cli.LocalLoginManager;
import jakarta.ws.rs.core.UriBuilder;

/**
 * Configuration for a single target (server and instance group). May use a direct specification or local login references.
 */
public class ServerExtension {

	private static final Logger log = LoggerFactory.getLogger(ServerExtension.class);
	
	private final String name;
	private final Property<String> uri;
	private final Property<String> token;
	private final Property<Boolean> useLogin;
	private final Property<String> login;
	private final Property<String> instanceGroup;

	/**
	 * @param name a name for the server configuration instance.
	 * @param factory the object factory to create properties from.
	 */
	public ServerExtension(String name, ObjectFactory factory) {
		this.name = name;

		this.uri = factory.property(String.class);
		this.token = factory.property(String.class);
		this.instanceGroup = factory.property(String.class);
		this.useLogin = factory.property(Boolean.class);
		this.login = factory.property(String.class);
	}

	/**
	 * @return the name of this server configuration
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param uri the URI of the server, must start with 'https', must end with '/api'.
	 */
	public void setUri(String uri) {
		String lower = uri.toLowerCase();
		if (!lower.startsWith("https://") || !lower.endsWith("/api")) {
			throw new IllegalStateException("The URI must start with 'https://' and end with '/api'");
		}
		this.uri.set(uri);
	}

	/**
	 * @param token the token used to authenticate with the server.
	 */
	public void setToken(String token) {
		this.token.set(token);
	}
	
	/**
	 * @return a constructed remote server which can be used to contact the referenced server.
	 */
	@Internal
	public RemoteService getRemote() {
		RemoteService result = null;
		if(Boolean.TRUE.equals(useLogin.getOrNull())) {
			LocalLoginManager llm = new LocalLoginManager();
			String lname = login.getOrNull();
			if(StringHelper.isNullOrEmpty(lname)) {
				result = llm.getCurrentService();
				if(result == null) {
					log.warn("Active login requested, but no login active");
				}
			} else {
				result = llm.getNamedService(lname);
				if(result == null) {
					log.warn("Login {} requested, but login does not exist", lname);
				}
			}
		} else {
			if(StringHelper.isNullOrEmpty(uri.getOrNull()) || StringHelper.isNullOrEmpty(token.getOrNull())) {
				log.warn("Server configuration incomplete for {}, missing uri or token.", name);
			} else {
				result = new RemoteService(UriBuilder.fromUri(uri.get()).build(), token.get());
			}
		}
		if(result == null) {
			throw new RuntimeException("Server configuration incomplete for " + name);
		}
		return result;
	}

	/**
	 * @return the target instance group to push into.
	 */
	public Property<String> getInstanceGroup() {
		return instanceGroup;
	}

	/**
	 * @param instanceGroup the instance group to use
	 */
	public void setInstanceGroup(String instanceGroup) {
		this.instanceGroup.set(instanceGroup);
	}
	
	/**
	 * @return whether to use a local login
	 */
	public Property<Boolean> getUseLogin() {
		return useLogin;
	}
	
	/**
	 * @param useLogin whether to use a local login
	 */
	public void setUseLogin(Boolean useLogin) {
		this.useLogin.set(useLogin);
	}
	
	/**
	 * @return the login to use. if not set, use the active one if useLogin is set.
	 */
	public Property<String> getLogin() {
		return login;
	}
	
	/**
	 * @param login the login to use. if not set, use the active one if useLogin is set.
	 */
	public void setLogin(String login) {
		this.login.set(login);
	}

}
