package io.bdeploy.ui;

/**
 * Represents the information attached to an instance about the managed master controlling it
 */
public class ControllingMasterConfiguration {

    private String name;

    /**
     * @return the name of the server as known in the {@link ManagedMasters}
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name of the server.
     */
    public void setName(String name) {
        this.name = name;
    }

}
