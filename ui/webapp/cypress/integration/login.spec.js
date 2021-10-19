//@ts-check

describe('Login Tests', function () {
  it('Visits start page and logs in', function () {
    cy.visit('/');
    cy.waitUntilContentLoaded();

    cy.url().should('include', '/login');
    cy.contains('BDeploy Login').should('exist');

    cy.screenshot('Doc_Login');

    cy.fillFormInput('user', 'admin');
    cy.fillFormInput('pass', 'admin');

    cy.get('button[type="submit"]').click();

    cy.url().should('include', '/groups/browser');
    cy.contains('Instance Groups');

    cy.getCookie('st').should('exist');
  });
});
