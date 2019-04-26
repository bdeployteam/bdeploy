/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin;

public class BDeployExternalDescriptor {

    public String manifest;

    public RemoteIncludingGroup from;

    public static class RemoteIncludingGroup {

        public String uri;
        public String token;
        public String group;
    }

}
