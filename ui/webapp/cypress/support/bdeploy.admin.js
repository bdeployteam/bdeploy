/**
 * Command: searchUser
 */
Cypress.Commands.add('searchUser', function(username = null) {
  cy.get('input[data-placeholder="Search..."').click().clear();
  if (username) {
    cy.get('input[data-placeholder="Search..."').type(username);
  }
})

/**
 * Command: createUser
 */
Cypress.Commands.add('createUser', function(username, fullname, email, password, docuScreenshots = false) {
  cy.get('.add-button').click();
  cy.contains('button', 'Apply').should('exist').and('be.disabled');

  cy.get('input[data-placeholder="Username"]').should('exist').click();
  cy.get('input[data-placeholder="Username"]').should('exist').and('have.focus').type(username);
  cy.get('input[data-placeholder="Full Name"]').type(fullname);
  cy.get('input[data-placeholder="E-Mail Address"]').type(email);
  cy.get('input[data-placeholder="New Password"]').type(password);
  cy.get('input[data-placeholder="Confirm New Password"]').type(password);
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
  cy.contains('tr', username).clickContextMenuDialog('Delete', 'Delete');

  cy.contains('div', 'Do you really want to delete user ' + username + '?').should('exist');

  cy.contains('button', 'OK').click();
})

/**
 * Command: setGlobalPermission
 */
Cypress.Commands.add('setGlobalPermission', function(username, permission, docuScreenshots = false) {
  cy.contains('tr', username).should('exist');
  cy.contains('tr', username).clickContextMenuDialog('Global Permissions', 'Global Permissions for');

  if (permission === '(none)') {
    cy.get('app-user-global-permissions').contains('span', permission).click();
  } else {
    cy.get('app-user-global-permissions').contains('mat-chip', permission).click();
  }

  if (docuScreenshots) {
    cy.screenshot('BDeploy_UserAccounts_SetGlobalPermissions');
  }
  cy.contains('button', 'OK').click();
})
