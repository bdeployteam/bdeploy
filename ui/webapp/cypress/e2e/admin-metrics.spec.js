//@ts-check

describe('Admin UI Tests (Metrics)', () => {
  beforeEach(() => {
    cy.login();
  });

  it('Tests Metrics', () => {
    cy.visit('/');
    cy.get('.local-hamburger-button').click();
    cy.get('button[data-cy=Administration]').click();

    cy.contains('a', 'Metrics').click();

    cy.inMainNavContent(() => {
      cy.contains('.mat-mdc-tab', 'HTTP').click();
      cy.contains('.mat-mdc-tab', 'HIVE').click();

      cy.intercept({ method: 'GET', url: '/api/server-monitor' }).as('server');
      cy.contains('.mat-mdc-tab', 'SERVER').click();
      cy.wait('@server');
    });
  });
});
