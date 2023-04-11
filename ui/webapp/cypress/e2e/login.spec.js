//@ts-check

describe('Login Tests', function () {
  it('Visits start page and logs in', function () {
    cy.visit('/');
    cy.waitUntilContentLoaded();

    cy.url().should('include', '/login');
    cy.contains('BDeploy Login').should('exist');

    cy.screenshot('Doc_Login');

    cy.fixture('login.json').then((user) => {
      cy.fillFormInput('user', user.user);
      cy.fillFormInput('pass', user.pass);
    });

    cy.get('button[type="submit"]').click();

    cy.url().should('include', '/groups/browser');
    cy.contains('Instance Groups');

    cy.getCookie('st').should('exist');

    cy.screenshot('Doc_SearchBarDisabled', {
      clip: { x: 0, y: 0, height: 80, width: 1280 },
    });
    cy.screenshot('Doc_EmptyGroups');
  });
});
