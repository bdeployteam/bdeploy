//@ts-check

describe('Admin UI Tests (Cleanup)', () => {
  beforeEach(() => {
    cy.login();
  });

  it('Tests Cleanup', () => {
    cy.visit('/');
    cy.get('.local-hamburger-button').click();
    cy.get('button[data-cy=Administration]').click();

    cy.contains('a', 'Manual Cleanup').click();

    cy.inMainNavContent(() => {
      cy.intercept({ method: 'GET', url: '/api/cleanUi' }).as('cleanup');
      cy.pressToolbarButton('Calculate');
      cy.wait('@cleanup');

      // nothing to clean yet, need to produce some data if we want to test more.
    });
  });
});
