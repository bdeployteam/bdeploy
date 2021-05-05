describe('Login to Web UI', function () {
  const currentUserFullName = 'John Doe';
  const currentUserEmail = 'John Doe@example.com';

  it('Visits start page and logs in', function () {
    cy.visit('/');

    cy.url().should('include', '/login');
    cy.contains('BDeploy Login');

    cy.get('app-bd-form-input[name="user"]').type('admin');
    cy.get('app-bd-form-input[name="pass"]').type('admin');

    cy.get('button[type="submit"]').click();

    cy.url().should('include', '/groups/browser');
    cy.contains('Instance Groups');

    cy.getCookie('st').should('exist');
  });
});
