package io.bdeploy.gradle.extensions;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

public class ServerExtension {

	private final String name;
	private final Property<String> uri;
	private final Property<String> token;
	private final Property<String> instanceGroup;

	public ServerExtension(String name, ObjectFactory factory) {
		this.name = name;

		this.uri = factory.property(String.class);
		this.token = factory.property(String.class);
		this.instanceGroup = factory.property(String.class);
	}

	/**
	 * @return the name of this server configuration
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return the URI of the server, must start with 'https', must end with '/api'.
	 */
	public Property<String> getUri() {
		return uri;
	}

	public void setUri(String uri) {
		String lower = uri.toLowerCase();
		if (!lower.startsWith("https://") || !lower.endsWith("/api")) {
			throw new IllegalStateException("The URI must start with 'https://' and end with '/api'");
		}
		this.uri.set(uri);
	}

	/**
	 * @return the token used to authenticate with the server.
	 */
	public Property<String> getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token.set(token);
	}

	/**
	 * @return the target instance group to push into.
	 */
	public Property<String> getInstanceGroup() {
		return instanceGroup;
	}

	public void setInstanceGroup(String instanceGroup) {
		this.instanceGroup.set(instanceGroup);
	}

}
