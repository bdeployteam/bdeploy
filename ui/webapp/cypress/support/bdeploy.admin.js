/**
 * Command: searchUser
 */
Cypress.Commands.add('searchUser', function(username = null) {
  cy.get('input[placeholder="Search..."').click().clear();
  if (username) {
    cy.get('input[placeholder="Search..."').type(username);
  }
})

/**
 * Command: createUser
 */
Cypress.Commands.add('createUser', function(username, fullname, email, password, docuScreenshots = false) {
  cy.contains('button', 'add').click();
  cy.contains('button', 'Apply').should('exist').and('be.disabled');

  cy.get('input[placeholder="Username"]').should('exist').click();
  cy.get('input[placeholder="Username"]').should('exist').and('have.focus').type(username);
  cy.get('input[placeholder="Full Name"]').type(fullname);
  cy.get('input[placeholder="E-Mail Address"]').type(email);
  cy.get('input[placeholder="New Password"]').type(password);
  cy.get('input[placeholder="Repeat New Password"]').type(password);
  cy.contains('button', 'Apply').should('exist').and('enabled');

  if (docuScreenshots) {
    cy.screenshot('BDeploy_UserAccounts_Add');
  }
  cy.contains('button', 'Apply').click();
})

/**
 * Command: deleteUser
 */
Cypress.Commands.add('deleteUser', function(username) {
  cy.visit('/#/admin/all/(panel:users)');
  cy.waitUntilContentLoaded();

  cy.contains('tr', username).should('exist');
  cy.contains('tr', username).clickContextMenuItem('Delete');

  cy.contains('div', 'Do you really want to delete user ' + username + '?').should('exist');

  cy.contains('button', 'OK').click();
})

/**
 * Command: setGlobalCapability
 */
Cypress.Commands.add('setGlobalCapability', function(username, capability, docuScreenshots = false) {
  cy.contains('tr', username).should('exist');
  cy.contains('tr', username).clickContextMenuItem('Global Permissions...');

  if (capability === '(none)') {
    cy.get('app-user-global-permissions').contains('span', capability).click();
  } else {
    cy.get('app-user-global-permissions').contains('mat-chip', capability).click();
  }

  if (docuScreenshots) {
    cy.screenshot('BDeploy_UserAccounts_SetGlobalCapabilities');
  }
  cy.contains('button', 'OK').click();
})
