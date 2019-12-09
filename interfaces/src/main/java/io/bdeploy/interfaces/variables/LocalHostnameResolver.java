package io.bdeploy.interfaces.variables;

import java.net.InetAddress;
import java.net.UnknownHostException;

import io.bdeploy.interfaces.variables.PrefixResolver;
import io.bdeploy.interfaces.variables.Variables;

/**
 * An additional variable resolver used by the DCU to resolve the local hostname.
 */
public class LocalHostnameResolver extends PrefixResolver {

    public LocalHostnameResolver() {
        super(Variables.HOST);
    }

    @Override
    protected String doResolve(String variable) {
        switch (variable) {
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

}
