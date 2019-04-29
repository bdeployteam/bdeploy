package io.bdeploy.launcher.cli;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.function.Function;

/**
 * An additional variable resolver used by the DCU to resolve the local hostname.
 */
public class LocalHostnameResolver implements Function<String, String> {

    @Override
    public String apply(String t) {
        if (t.startsWith("H:")) {
            String var = t.substring(2);
            switch (var) {
                case "HOSTNAME":
                    try {
                        return InetAddress.getLocalHost().getHostName();
                    } catch (UnknownHostException e) {
                        return "localhost";
                    }
                default:
                    return null;
            }
        }
        return null;
    }

}
