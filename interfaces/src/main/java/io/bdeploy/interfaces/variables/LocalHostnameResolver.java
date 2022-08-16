package io.bdeploy.interfaces.variables;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An additional variable resolver used by the DCU to resolve the local hostname.
 */
public class LocalHostnameResolver extends PrefixResolver {

    private static final Logger log = LoggerFactory.getLogger(LocalHostnameResolver.class);
    private final boolean client;

    /**
     * @param client whether the resolution happens on a client node, this will cause an info log to be issued.
     */
    public LocalHostnameResolver(boolean client) {
        super(Variables.HOST);

        this.client = client;
    }

    @Override
    protected String doResolve(String variable) {
        switch (variable) {
            case "HOSTNAME":
                if (client) {
                    log.info("Resolving H:HOSTNAME on client; this might not be what is expected!");
                }
                try {
                    return InetAddress.getLocalHost().getHostName();
                } catch (UnknownHostException e) {
                    return "localhost";
                }
            default:
                return null;
        }
    }

}
