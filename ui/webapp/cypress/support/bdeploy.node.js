Cypress.Commands.add('getNodeCard', function(name) {
  return cy.contains('app-instance-node-card', name);
})

Cypress.Commands.add('getApplicationConfigCard', function(node, name) {
  return cy.getNodeCard(node).contains('app-application-configuration-card', name);
})

Cypress.Commands.add('waitUntilContentLoaded', function() {
  // this delay is here to allow the web-app to actually issue a request that we will be waiting for.
  // otherwise this method will lookup DOM elements which will be created right afterwards.
  cy.wait(100);

  cy.get('ngx-loading-bar').children().should('not.exist'); // page not yet loaded fully (e.g. lazy loading large module)
  cy.get('mat-progress-spinner').should('not.be.visible'); // determinate
  cy.get('mat-spinner').should('not.be.visible'); // indeterminate
})
