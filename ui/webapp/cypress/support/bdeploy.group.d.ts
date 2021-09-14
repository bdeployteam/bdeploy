declare namespace Cypress {
  interface Chainable<Subject> {
    /**
     * @param groupName the name of the group.
     * @param mode the BDeploy mode to visit when creating.
     */
    createGroup(groupName: string, mode?: string);

    /**
     * @param groupName the name of the group.
     * @param mode the BDeploy mode to visit when deleting.
     */
    deleteGroup(groupName: string, mode?: string);

    /**
     * @param groupName the name of the group.
     * @param fileName the file within the fixtures directory to use.
     * @param mode the BDeploy mode to visit when uploading.
     */
    uploadProductIntoGroup(groupName: string, fileName: string, mode?: string);

    /**
     * @param groupName the name of the group.
     * @param productName the name of the product to look for
     * @param version the version which is expected to exist.
     * @param mode the BDeploy mode to visit when checking.
     */
    verifyProductVersion(groupName: string, productName: string, version: string, mode?: string);

    /**
     * Enters an instance group. This expects the web app to be on the group browser.
     *
     * @param groupName the name of the group to look for.
     */
    enterGroup(groupName: string);

    /**
     * Assuming the named group has been created on the central server, this method
     * will perform required steps on CENTRAL and MANAGED server to attach the group
     * to the managed server.
     *
     * @param groupName the name of the group to attach.
     */
    attachManaged(groupName: string);
  }
}
