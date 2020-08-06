describe('Login to Web UI', function () {
  it('Visits start page and logs in', function () {
    cy.visit('/');

    cy.url().should('include', '/login')
    cy.contains('Login');

    cy.get('[data-placeholder="Username"]').type('admin');
    cy.get('[data-placeholder="Password"]').type('admin');

    cy.get('[type="submit"]').click();

    cy.url().should('include', '/instancegroup/browser')
    cy.contains('Instance Groups')

    cy.getCookie('st').should('exist')
  })
})
