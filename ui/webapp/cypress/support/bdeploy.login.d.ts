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
     * Visit BDeploy :)
     */
    visitCentral(url: string);
    visitManaged(url: string);
    visitBDeploy(url: string, mode: 'STANDALONE' | 'CENTRAL' | 'MANAGED');

    /**
     * Forces (re-)visit of the given URL by adding a random timestamp query parameter to the URL
     *
     * This effectively disables the detection that we're already on that URL which can be wrong, e.g. when switching
     * from managed to central and vice versa.
     */
    forceVisit(url: string);

    /** performs an authenticated request */
    authenticatedRequest(req: Partial<RequestOptions>): Chainable<Cypress.Response<any>>;
  }
}
