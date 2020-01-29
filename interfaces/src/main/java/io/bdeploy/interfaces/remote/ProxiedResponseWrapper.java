package io.bdeploy.interfaces.remote;

import java.util.List;
import java.util.Map;

public class ProxiedResponseWrapper {

    public int responseCode;
    public String responseReason;

    public Map<String, List<String>> headers;

    public String base64body;

}
