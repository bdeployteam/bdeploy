package io.bdeploy.common.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Something URL-like", which can be split into scheme, host, port and "the rest".
 * Each of the individual parts can be modified before using toString to get back out a "similar URL-like thing".
 */
public class URLish {

    /**
     * Groups matched by this regular expression:
     * 0: all
     * 1: scheme including ://
     * 2: host with port if present
     * 3: host if port is present
     * 4: port with leading : if port is present
     * 5: port if port is present
     * 6: host if port is *not* present
     * 7: host if port is *not* present and a trailing path *is* present
     * 8: host if port is *not* present and a trailing path is *not* present
     * 9: trailing path if present
     * We simply have to use groups 1, 3, 5, 6 and 9 :)
     */
    private static final Pattern URL_ISH = Pattern.compile("(\\S+:\\/\\/)?((.+(?=:\\d))(:(\\d+))|((.+(?=\\/))|(.+)))?(\\/.*)?");

    public String scheme;
    public String hostname;
    public String port;
    public String pathAndQuery;

    public URLish(String url) {
        Matcher matcher = URL_ISH.matcher(url);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Not a valid URLish: " + url);
        }

        this.scheme = matcher.group(1);
        this.hostname = matcher.group(3) != null ? matcher.group(3) : matcher.group(6); // either with or without port matched
        this.port = matcher.group(5);
        this.pathAndQuery = matcher.group(9);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        if (scheme != null) {
            builder.append(scheme);
        }

        builder.append(hostname);

        if (port != null) {
            builder.append(':');
            builder.append(port);
        }

        if (pathAndQuery != null) {
            builder.append(pathAndQuery);
        }

        return builder.toString();
    }

}
