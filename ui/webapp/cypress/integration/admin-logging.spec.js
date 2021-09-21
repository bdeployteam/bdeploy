//@ts-check

describe('Admin UI Tests (Logging)', () => {
  beforeEach(() => {
    cy.login();
  });

  it('Tests Logging', () => {
    cy.visit('/');
    cy.get('.local-hamburger-button').click();
    cy.get('button[data-cy=Administration]').click();

    cy.contains('a', 'Logging').click();

    cy.intercept({ method: 'GET', url: '/api/logging-admin/config' }).as('config');
    cy.intercept({ method: 'POST', url: '**/api/logging-admin/content/master**' }).as('log');

    cy.inMainNavContent(() => {
      cy.get('tr').should('have.length.above', 1); // two audit logs which should always exist.

      cy.pressToolbarButton('Edit Configuration');
    });

    cy.inMainNavFlyin('app-log-config-editor', () => {
      cy.get('button[data-cy^="APPLY"]').should('exist').and('be.disabled');
      cy.wait('@config');
      cy.pressToolbarButton('Close');
    });

    cy.inMainNavContent(() => {
      cy.contains('tr', 'audit.log').click();
    });

    cy.inMainNavFlyin('app-log-file-viewer', () => {
      cy.wait('@log');
      cy.pressToolbarButton('Close');
    });
  });
});
