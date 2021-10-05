//@ts-check

describe('Admin UI Tests (Update)', () => {
  beforeEach(() => {
    cy.login();
  });

  it('Tests Update', () => {
    cy.visit('/');
    cy.get('.local-hamburger-button').click();
    cy.get('button[data-cy=Administration]').click();

    cy.contains('a', 'BDeploy Update').click();

    cy.inMainNavContent(() => {
      cy.contains('tr', 'installed').click();
    });

    cy.inMainNavFlyin('app-software-details', () => {
      cy.get('button[data-cy^="Install"]').should('exist').and('be.disabled');
      cy.get('button[data-cy^="Delete"]').should('exist').and('be.disabled');
    });
  });
});
