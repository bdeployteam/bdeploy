declare namespace Cypress {
  interface Chainable<Subject> {
    /**
     * Log in to the configured backend
     * @example cy.login()
     */
    login();

    /**
     * Creates a new instance group with the given name.
     * @param group the name of the instance group to create
     */
    createInstanceGroup(group: string);

    /**
     * Uploads the given product into the given instance group
     * @param group the name of the instance group
     * @param product the name of the ZIP archive as stored in the fixtures folder
     */
    uploadProductIntoGroup(group: string, product: string);

    /**
     * Verify that the given product version exists
     * @param group the name of the instance group
     * @param productName the name of the product as displayed in the ui
     * @param productId the internal ID of the product
     * @param version the expected product version
     */
    verifyProductVersion(group:string, productName:string, productId: string, version: string);

    /**
     * Delete the instance group with the given name.
     * @param group the name of the instance group to delete
     */
    deleteInstanceGroup(group: string);

    /**
     * Create a new instance in the given instance group
     * @param group the name of the instance group to create into.
     * @param name the name of the instance to create
     * @returns the UUID of the created instance as string
     * @example cy.createInstance('Test')
     */
    createInstance(group: string, name: string, version?: '1.0.0' | '2.0.0'): Chainable<string>;

    /**
     * Delete a previously created instance and verify it is gone.
     * @param group the name of the instance group the instance is found in.
     * @param instanceUuid the UUID of the instance to delete.
     */
    deleteInstance(group: string, instanceUuid: string);

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
     * Chain off a clickable element which will trigger downloadLocation.assign in the application.
     * @param filename the name of the target file in the cypress/fixtures directory.
     */
    downloadFile(filename: string);

    /**
     * Waits until the content of the page is loaded.
     */
    waitUntilContentLoaded();
  }
}
