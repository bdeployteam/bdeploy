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
  }
}
