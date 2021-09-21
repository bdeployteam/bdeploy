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
      cy.fillFormSelect(undefined, 'HTTP');
      cy.fillFormSelect(undefined, 'HIVE');

      cy.intercept({ method: 'GET', url: '/api/server-monitor' }).as('server');
      cy.fillFormSelect(undefined, 'SERVER');
      cy.wait('@server');
    });
  });
});
