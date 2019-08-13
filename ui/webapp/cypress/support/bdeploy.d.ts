declare namespace Cypress {
  interface Chainable<Subject> {
    /**
     * Log in to the configured backend
     * @example cy.login()
     */
    login();

    /**
     * Create a new instance in the 'Test' instance group
     * @param name the name of the instance to create
     * @example cy.createInstance('Test')
     */
    createInstance(name: string);
  }
}
