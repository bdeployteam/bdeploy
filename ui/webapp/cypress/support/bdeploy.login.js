Cypress.Commands.add('login', function() {
  cy.fixture('login.json').then(user => {
    cy.request('POST', Cypress.env('backendBaseUrl') + '/auth', { user: user.user, password: user.pass })
    cy.getCookie('st').should('exist')
  })
})
