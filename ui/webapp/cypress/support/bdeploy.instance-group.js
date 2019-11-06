/**
 * Command: createInstanceGroup
 */
Cypress.Commands.add('createInstanceGroup', function(name) {
  cy.visit('/');
  cy.waitUntilContentLoaded();

  cy.contains('button', 'add').click();

  cy.contains('button', 'SAVE').should('exist').and('be.disabled');
  cy.get('input[placeholder^="Instance group name"]').type(name);
  cy.get('input[placeholder=Description]').type(name);

  cy.fixture('bdeploy.png').then(fileContent => {
    cy.get('input[type=file]').upload({ fileContent: fileContent, fileName: 'bdeploy.png', mimeType: 'image/png' });
  });

  cy.get('.logo-img').should('exist');

  cy.contains('button', 'SAVE').click();

  cy.get('[data-cy=group-' + name + ']').should('exist');
})

/**
 * Command: deleteInstanceGroup
 */
Cypress.Commands.add('deleteInstanceGroup', function(name) {
  cy.visit('/');

  cy.get('[data-cy=group-' + name + ']')
    .should('exist')
    .clickContextMenuItem('Delete');
  cy.contains('mat-dialog-container', 'Delete Instance Group: ' + name)
    .should('exist')
    .within(dialog => {
      cy.contains('Deleting an instance group cannot be undone').should('exist');
      cy.contains('button', 'Delete').should('be.disabled');
      cy.get('input[placeholder="Instance Group Name"]')
        .clear()
        .type(name);
      cy.contains('button', 'Delete')
        .should('be.enabled')
        .click();
    });

  cy.get('[data-cy=group-' + name + ']').should('not.exist');
})

/**
 * Command: uploadProductIntoGroup
 */
Cypress.Commands.add('uploadProductIntoGroup', function(groupName,fileName) {
  cy.visit('/');
  cy.waitUntilContentLoaded();

  cy.get('[data-cy=group-' + groupName + ']').first().should('exist').click();
  cy.waitUntilContentLoaded();

  cy.get('button[mattooltip="Manage Products..."]').should('be.visible').and('be.enabled').click();
  cy.contains('button', 'cloud_upload').should('be.visible').and('be.enabled').click();

  cy.get('mat-dialog-container').within(() => {
    cy.fixture(fileName).then(zip => {
      cy.get('input[type=file]').upload({
        fileName: fileName,
        fileContent: zip,
         mimeType: 'application/zip',
      });
    });

    cy.contains('button', 'Upload').should('be.enabled').click();
    cy.get('td:contains("Upload successful")').should('have.length', 1);

    cy.contains('button', 'Close').should('be.visible').and('be.enabled').click();
  });
})

 /**
 * Command: verifyProductVersion
 */
Cypress.Commands.add('verifyProductVersion', function(groupName, productName, productId, productVersion) {
  cy.visit('/');
  cy.waitUntilContentLoaded();

  cy.get('[data-cy=group-' + groupName + ']').first().should('exist').click();
  cy.waitUntilContentLoaded();

  cy.get('button[mattooltip="Manage Products..."]').should('be.visible').and('be.enabled').click();
  cy.contains('app-product-card', productName).should('exist').click();

  cy.get('app-product-list').contains(productVersion).should('exist');
  cy.contains('app-product-list', productVersion).within(() => {
    cy.contains('button', 'info').should('be.visible').click();
  });

  cy.get('mat-card.info').within(() => {
    cy.contains('mat-chip', 'X-Product').contains(productId).should('exist');
  });
})

