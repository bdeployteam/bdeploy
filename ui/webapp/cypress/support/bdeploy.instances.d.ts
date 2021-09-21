declare namespace Cypress {
  interface Chainable<Subject> {
    /**
     * @param groupName the name of the instance group to create the instance in.
     * @param instanceName the name of the instance.
     * @param prodName the product to use
     * @param prodVersion the product version to use.
     * @param mode the mode of BDeploy to use.
     */
    createInstance(groupName: string, instanceName: string, prodName: string, prodVersion: string, mode?: string);

    /**
     * @param groupName the name of the instance group to search the instance in.
     * @param instanceName the name of the instance to delete
     * @param mode the mode of BDeploy to use.
     */
    deleteInstance(groupName: string, instanceName: string, mode?: string);

    /**
     * @param groupName the name of the instance group hosting the instance.
     * @param instanceName the name of the instance to enter
     * @param mode the server mode.
     */
    enterInstance(groupName: string, instanceName: string, mode?: string);
  }
}
