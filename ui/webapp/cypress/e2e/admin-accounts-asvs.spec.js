//@ts-check

describe('Admin UI Tests (Accounts ASVS)', () => {
  beforeEach(() => {
    cy.login();
  });

  it('Tests ASVS Password Rules', () => {
    cy.visit('/');
    cy.get('.local-hamburger-button').click();
    cy.get('button[data-cy=Administration]').click();

    cy.contains('a', 'User Accounts').click();

    // test password rules
    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Create User');
      cy.intercept({ method: 'PUT', url: '/api/auth/admin/local' }).as('createUser');
    });

    cy.inMainNavFlyin('add-user', () => {
      cy.fillFormInput('name', 'test');
      cy.fillFormInput('fullName', 'Test User');
      cy.fillFormInput('email', 'example@example.org');
      cy.fillFormInput('pass', 'p');
      cy.fillFormInput('passConfirm', 'p');

      cy.contains('New Password must be at least 12 characters.').should('exist');
      cy.fillFormInput('pass', 'p'.repeat(129));
      cy.contains('New Password must be at maximum 128 characters.').should('exist');

      cy.fillFormInput('pass', 'NewPassword1');
      cy.fillFormInput('passConfirm', 'NewPassword1');

      cy.get('[data-strength=4]').should('exist');
    });
  });
});
