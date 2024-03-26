declare namespace Cypress {
  interface Chainable<Subject> {
    /**
     * @param groupName the name of the group.
     */
    createGroup(groupName: string);

    /**
     * @param groupName the name of the group.
     */
    deleteGroup(groupName: string);

    /**
     * @param groupName the name of the group.
     * @param fileName the file within the fixtures directory to use.
     */
    uploadProductIntoGroup(groupName: string, fileName: string, screenshots?: boolean);

    /**
     * @param groupName the name of the group.
     * @param productName the name of the product to look for
     * @param version the version which is expected to exist.
     */
    verifyProductVersion(groupName: string, productName: string, version: string);

    /**
     * Enters an instance group. This expects the web app to be on the group browser.
     *
     * @param groupName the name of the group to look for.
     */
    enterGroup(groupName: string);

    /**
     * Assuming the named group has been created on the central server, this method
     * will perform required steps on MANAGED server to attach the group
     * to the managed server.
     *
     * @param groupName the name of the group to attach.
     */
    attachManagedSide(groupName: string);

    /**
     * Assuming the named group has been created on the central server, this method
     * will perform required steps on CENTRAL server to attach the group
     * to the managed server.
     *
     * @param groupName the name of the group to attach.
     */
    attachCentralSide(groupName: string);

    /**
     * Quickly gets rid of all instance groups via REST API.
     */
    cleanAllGroups(mode?: 'STANDALONE' | 'MANAGED' | 'CENTRAL');

    /**
     * Quickly gets rid of all software repositories via REST API.
     */
    cleanAllSoftwareRepos(mode?: 'STANDALONE' | 'MANAGED' | 'CENTRAL');
  }
}
