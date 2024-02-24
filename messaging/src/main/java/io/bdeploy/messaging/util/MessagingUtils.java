package io.bdeploy.messaging.util;

import java.util.regex.Pattern;

import io.bdeploy.common.util.StringHelper;
import jakarta.mail.URLName;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

/**
 * Contains utility methods for message handling.
 */
public class MessagingUtils {

    public static final Pattern MAIL_ADDRESS_PATTERN = Pattern.compile("[a-zA-Z0-9-_\\.]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]+");

    /**
     * Builds a new {@link URLName} which copies the protocol-, host-, port- and file-part of the given url, but sets the provided
     * username and password.
     *
     * @param url The base-{@link URLName}
     * @param username The username to set to the new {@link URLName}
     * @param password The password to set to the new {@link URLName}
     * @return A newly created {@link URLName}
     * @throws IllegalArgumentException If no url was provided or any mandatory parameter is missing
     */
    public static URLName checkAndParseUrl(String url, String username, String password) {
        if (StringHelper.isNullOrBlank(url)) {
            throw new IllegalArgumentException("No URL was provided.");
        }

        URLName tempUrl = new URLName(url.trim());

        String protocol = tempUrl.getProtocol();
        if (StringHelper.isNullOrBlank(protocol)) {
            throw new IllegalArgumentException("No protocol was set.");
        }

        String host = tempUrl.getHost();
        if (StringHelper.isNullOrBlank(host)) {
            throw new IllegalArgumentException("No host was set.");
        }

        int port = tempUrl.getPort();
        if (port == -1) {
            throw new IllegalArgumentException("No port was set.");
        }

        if (username != null) {
            username = username.trim();
        }

        if (password != null && password.isEmpty()) {
            password = null;
        }

        String file = tempUrl.getFile();
        return new URLName(protocol.trim(), host.trim(), port, file == null ? null : file.trim(), username, password);
    }

    /**
     * Converts the given address to an {@link InternetAddress}.
     *
     * @param address The address to parse
     * @return The newly created {@link InternetAddress}
     * @throws IllegalArgumentException If no address was provided or it cannot be parsed
     */
    public static InternetAddress checkAndParseAddress(String address) {
        if (StringHelper.isNullOrBlank(address)) {
            throw new IllegalArgumentException("No address was provided.");
        }
        try {
            return new InternetAddress(address.trim());
        } catch (AddressException e) {
            throw new IllegalArgumentException("Failed to parse address.", e);
        }
    }
}
