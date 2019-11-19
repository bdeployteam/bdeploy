Cypress.Commands.add('login', function() {
  cy.fixture('login.json').then(user => {
    cy.request('POST', Cypress.env('backendBaseUrl') + '/auth', { user: user.user, password: user.pass })
    cy.getCookie('st').should('exist')
  })
})

Cypress.Commands.add('loginCentral', function() {
  cy.fixture('login.json').then(user => {
    cy.request('POST', Cypress.env('backendBaseUrlCentral') + '/auth', { user: user.user, password: user.pass })
    cy.getCookie('st').should('exist')
  })
})

Cypress.Commands.add('loginManaged', function() {
  cy.fixture('login.json').then(user => {
    cy.request('POST', Cypress.env('backendBaseUrlManaged') + '/auth', { user: user.user, password: user.pass })
    cy.getCookie('st').should('exist')
  })
})

Cypress.Commands.add('visitCentral', function(url) {
  cy.loginCentral();
  cy.visit(Cypress.env('baseUrlCentral') + url);
})

Cypress.Commands.add('visitManaged', function(url) {
  cy.loginManaged();
  cy.visit(Cypress.env('baseUrlManaged') + url);
})
