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
      cy.contains('.mat-tab-label', 'HTTP').click();
      cy.contains('.mat-tab-label', 'HIVE').click();

      cy.intercept({ method: 'GET', url: '/api/server-monitor' }).as('server');
      cy.contains('.mat-tab-label', 'SERVER').click();
      cy.wait('@server');
    });
  });
});
