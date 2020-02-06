declare namespace Cypress {
  interface Chainable<Subject> {
    /**
     * Log in to the configured backend
     * @example cy.login()
     */
    login();
    loginCentral();
    loginManaged();

    /**
     * Creates a new instance group with the given name.
     * @param group the name of the instance group to create
     * @param mode the BDeploy mode to use when navigating
     */
    createInstanceGroup(group: string, mode?: 'STANDALONE' | 'CENTRAL');

    /**
     * Uploads the given product into the given instance group
     * @param group the name of the instance group
     * @param product the name of the ZIP archive as stored in the fixtures folder
     * @param mode the BDeploy mode to use when navigating
     */
    uploadProductIntoGroup(group: string, product: string, mode?: 'STANDALONE' | 'CENTRAL' | 'MANAGED');

    /**
     * Verify that the given product version exists
     * @param group the name of the instance group
     * @param productName the name of the product as displayed in the ui
     * @param productId the internal ID of the product
     * @param version the expected product version
     * @param mode the BDeploy mode to use when navigating
     */
    verifyProductVersion(group: string, productName: string, productId: string, version: string, mode?: 'STANDALONE' | 'CENTRAL' | 'MANAGED');

    /**
     * Delete the instance group with the given name.
     * @param group the name of the instance group to delete
     * @param mode the BDeploy mode to use when navigating
     */
    deleteInstanceGroup(group: string, mode?: 'STANDALONE' | 'CENTRAL' | 'MANAGED');

    /**
     * Attaches the managed server to the central server.
     * @param group the name of the instance group to attach
     */
    attachManaged(group: string);

    /**
     * Create a new instance in the given instance group
     * @param group the name of the instance group to create into.
     * @param name the name of the instance to create
     * @param mode the BDeploy mode to use when navigating
     * @param version the product version to use
     * @param server the server to configure if mode is CENTRAL
     * @returns the UUID of the created instance as string
     * @example cy.createInstance('Test')
     */
    createInstance(group: string, name: string, mode?: 'STANDALONE' | 'CENTRAL' | 'MANAGED', version?: '1.0.0' | '2.0.0', server?: string): Chainable<string>;

    /**
     * Delete a previously created instance and verify it is gone.
     * @param group the name of the instance group the instance is found in.
     * @param instanceUuid the UUID of the instance to delete.
     * @param mode the BDeploy mode to use when navigating
     */
    deleteInstance(group: string, instanceUuid: string, mode?: 'STANDALONE' | 'CENTRAL' | 'MANAGED');

    /**
     * Navigate to the given instance
     * @param groupName the name of the instance group the instance is found in.
     * @param instanceUuid  the UUID of the instance to go to.
     * @param mode the BDeploy mode to use when navigating
     */
    gotoInstance(groupName: string, instanceUuid: string, mode?: 'STANDALONE' | 'CENTRAL' | 'MANAGED');

    /**
     * Perform a dummy change on the given instance to create a new instance version.
     * @param instanceGroupName the name of the instance group the instance lives within
     * @param instanceUuid the instance UUID.
     * @param nodeName name of the node the application is configured on
     * @param applicationName the name of the application to modify (toggle keep-alive)
     * @param mode the BDeploy mode to use when navigating
     */
    createNewInstanceVersionByDummyChange(instanceGroupName: string, instanceUuid: string, nodeName: string, applicationName: string, mode?: 'STANDALONE' | 'CENTRAL' | 'MANAGED');

    /**
     * Finds the 'app-instance-node-card' for the given name.
     * @param name the name of the node to find
     */
    getNodeCard(name: string): Chainable<Subject>;

    /**
     * Finds the first 'app-application-configuration-card' for the given name in the given node.
     * @param node the name of the node to look into.
     * @param name the name of the application to find.
     */
    getApplicationConfigCard(node: string, name: string): Chainable<Subject>;

    /**
     * Finds the latest (topmost) 'app-instance-version-card'
     */
    getLatestInstanceVersion(): Chainable<Subject>;

    /**
     * Finds the active instance version
     */
    getActiveInstanceVersion(): Chainable<Subject>;

    /**
     * Chained off an instance version card, will install and activate this instance version.
     */
    installAndActivate(): Chainable<Subject>;

    /**
     * Starts the Server Application on master
     */
    startProcess(node: string, application: string);

        /**
     * Chained off an instance version card, will activate this instance version.
     */
    activate(): Chainable<Subject>;

    /**
     * Adds the named optional parameter in the given panel and sets its value.
     * @param panel the name of the group (panel)
     * @param name the name of the optional parameter to add and set
     * @param value the value to set the parameter to.
     */
    addAndSetOptionalParameter(panel: string, name: string, value: string): void;

    /**
     * Converts the given missing parameter to a custom parameter.
     * @param id the identifier of the parameter
     * @param name the name of the missing parameter
     */
    convertMissingToCustomParameter(name: string): void;

    /**
     * Removes the given missing parameter.
     * @param name the name of the missing parameter
     */
    deleteMissingParameter(name: string): void;

    /**
     * Drag and drop emulation helper.
     * @param target the target to drop onto. Can be a selector or DOM element.
     * @param opts additional options
     */
    dragTo(target: any, opts?: { delay?: number, steps?: number, smooth?: boolean});

    /**
     * Chained off any DOM element, will try to find the context menu within this element
     * (looking for a button using the 'more_vert' icon) and then click the named item within.
     * @param item the context menu item to click
     * @returns the original subject the command has been chained of.
     */
    clickContextMenuItem(item: string): Chainable<Subject>;

    /**
     * Chain off a clickable element which will trigger downloadLocation.click in the application.
     * @param callback the callback to receive the response of the request (i.e. the downloaded data).
     */
    downloadBlobFileShould(callback: (any) => void);

    /**
     * Chain off a clickable element which will trigger downloadLocation.click in the application.
     * @param filename the filename to store the file as - in the cypress/fixtures directory
     */
    downloadBlobFile(filename: string);

    /**
     * Chain off a clickable element which will trigger downloadLocation.assign in the application.
     * @param filename the name of the target file in the cypress/fixtures directory.
     */
    downloadFile(filename: string);

    /**
     * Waits until the content of the page is loaded.
     * <p>
     * NOTE: Don't use with 'within', as elements would not be found in narrowed scope.
     */
    waitUntilContentLoaded();

    visitCentral(url: string);
    visitManaged(url: string);

    visitBDeploy(url: string, mode: 'STANDALONE' | 'CENTRAL' | 'MANAGED');

    /**
     * Types some text into the ACE editor component
     */
    typeInAceEditor(text: string);
  }
}
