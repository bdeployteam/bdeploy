declare namespace Cypress {
  interface Chainable<Subject> {
    /**
     * @param groupName the name of the instance group to create the instance in.
     * @param instanceName the name of the instance.
     * @param prodName the product to use
     * @param prodVersion the product version to use.
     */
    createInstance(groupName: string, instanceName: string, prodName: string, prodVersion: string);

    /**
     * @param groupName the name of the instance group to search the instance in.
     * @param instanceName the name of the instance to delete
     */
    deleteInstance(groupName: string, instanceName: string);

    /**
     * @param groupName the name of the instance group hosting the instance.
     * @param instanceName the name of the instance to enter
     */
    enterInstance(groupName: string, instanceName: string);
  }
}
