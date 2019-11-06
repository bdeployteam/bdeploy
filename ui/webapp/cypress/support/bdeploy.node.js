Cypress.Commands.add('getNodeCard', function(name) {
  return cy.contains('app-instance-node-card', name);
})

Cypress.Commands.add('getApplicationConfigCard', function(node, name) {
  return cy.getNodeCard(node).contains('app-application-configuration-card', name);
})

Cypress.Commands.add('waitUntilContentLoaded', function() {
  cy.get('mat-progress-spinner').should('not.exist'); // determinate
  cy.get('mat-spinner').should('not.exist'); // indeterminate
})
