/**
 * Command: createInstanceGroup
 */
Cypress.Commands.add('createInstanceGroup', function(name, mode = 'STANDALONE') {
  cy.visitBDeploy('/', mode);
  cy.waitUntilContentLoaded();

  cy.contains('button', 'add').click();

  cy.contains('button', 'SAVE').should('exist').and('be.disabled');
  cy.get('input[data-placeholder^="Instance group ID"]').should('exist').click();
  cy.get('input[data-placeholder^="Instance group ID"]').should('exist').and('have.focus').type(name);
  cy.get('input[data-placeholder=Description]').type(name);

  cy.get('input[type=file]').attachFile({ filePath: 'bdeploy.png', mimeType: 'image/png' });

  cy.get('.logo').should('exist');

  cy.contains('button', 'SAVE').click();
  cy.waitUntilContentLoaded();

  cy.get('[data-cy=group-' + name + ']').should('exist');
})

/**
 * Command: deleteInstanceGroup
 */
Cypress.Commands.add('deleteInstanceGroup', function(name, mode = 'STANDALONE') {
  cy.visitBDeploy('/', mode);

  cy.get('[data-cy=group-' + name + ']')
    .should('exist')
    .clickContextMenuDialog('Delete');
  cy.contains('mat-dialog-container', 'Delete Instance Group: ' + name)
    .should('exist')
    .within(dialog => {
      cy.contains('Deleting an instance group cannot be undone').should('exist');
      cy.contains('button', 'Delete').should('be.disabled');
      cy.get('input[data-placeholder="Instance Group ID"]')
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
Cypress.Commands.add('uploadProductIntoGroup', function(groupName,fileName, mode = 'STANDALONE') {
  cy.visitBDeploy('/', mode);
  cy.waitUntilContentLoaded();

  cy.get('[data-cy=group-' + groupName + ']').first().should('exist').click();
  cy.waitUntilContentLoaded();

  cy.get('mat-toolbar').clickContextMenuAction('Products');
  cy.contains('button', 'cloud_upload').should('be.visible').and('be.enabled').click();

  cy.get('mat-dialog-container').within(() => {
    cy.get('input[type=file]').attachFile({
      filePath: fileName,
      mimeType: 'application/zip',
    });

    cy.contains('button', 'Upload').should('be.enabled').click();
    cy.get('td:contains("Upload successful")').should('have.length', 1);

    cy.contains('button', 'Close').should('be.visible').and('be.enabled').click();
  });
})

 /**
 * Command: verifyProductVersion
 */
Cypress.Commands.add('verifyProductVersion', function(groupName, productName, productId, productVersion, mode = 'STANDALONE') {
  cy.visitBDeploy('/', mode);
  cy.waitUntilContentLoaded();

  cy.get('[data-cy=group-' + groupName + ']').first().should('exist').click();
  cy.waitUntilContentLoaded();

  cy.get('mat-toolbar').clickContextMenuAction('Products');
  cy.contains('app-product-card', productName).should('exist').click();

  cy.get('app-product-list').contains(productVersion).should('exist');
  cy.contains('app-product-list', productVersion).within(() => {
    cy.contains('button', 'info').should('be.visible').click();
  });

  cy.get('mat-card.info').within(() => {
    cy.contains('mat-chip', 'X-Product').contains(productId).should('exist');
  });
})

Cypress.Commands.add('attachManaged', function(groupName, screenshot = false) {
  cy.visitBDeploy('/', 'MANAGED');

  cy.waitUntilContentLoaded();
  if(screenshot) {
    cy.screenshot('BDeploy_Welcome_Managed');
  }
  cy.contains('button', 'link').should('exist').and('be.enabled').click();

  cy.waitUntilContentLoaded();
  if(screenshot) {
    cy.screenshot('BDeploy_Managed_Attach_Intro');
  }
  cy.contains('button', 'Next').should('exist').and('be.enabled').click();
  if(screenshot) {
    cy.wait(100);
    cy.screenshot('BDeploy_Managed_Attach_Waiting');
  }
  cy.contains('button', 'Continue Manually').should('exist').and('be.enabled').click();

  cy.contains('button', 'Download').should('exist').and('be.enabled').downloadBlobFile('managed-ident.txt');

  cy.visitBDeploy('/', 'CENTRAL');
  cy.get('[data-cy=group-' + groupName + ']')
    .should('exist')
    .clickContextMenuAction('Managed Servers');

  cy.waitUntilContentLoaded();
  if(screenshot) {
    cy.screenshot('BDeploy_Central_Managed_Servers');
  }

  cy.contains('button', 'add').should('exist').and('be.enabled').click();
  cy.contains('button', 'Next').should('exist').and('be.enabled').click();

  if(screenshot) {
    cy.wait(100)
    cy.screenshot('BDeploy_Central_Attach_Drop');
  }

  cy.contains('mat-step-header', 'Attach Managed Server').parent().within(e => {
    cy.get('input[data-cy="managed-ident"]').attachFile({
      filePath: 'managed-ident.txt',
      mimeType: 'text/plain',
    });

    cy.contains('Successfully read information for').should('exist').and('be.visible');
    if(screenshot) {
      cy.screenshot('BDeploy_Central_Attach_Read_Success')
    }
    cy.contains('button', 'Next').should('exist').and('be.visible').and('be.enabled').click();
  })

  cy.contains('mat-step-header', 'Additional Information').parent().within(e => {
    cy.get('input[data-placeholder=Description]').should('exist').and('be.visible').and('be.empty').type('Managed Server');
    if(screenshot) {
      cy.screenshot('BDeploy_Central_Attach_Info')
    }
    cy.contains('button', 'Next').should('exist').and('be.enabled').scrollIntoView().click();
  });

  // magic happens here in the background :)

  if(screenshot) {
    cy.waitUntilContentLoaded();
    cy.contains('mat-step-header', 'Done').parent().within(e => {
      cy.contains('button', 'Done').should('exist').and('be.enabled').scrollIntoView();
    });
    cy.wait(100); // animation
    cy.screenshot('BDeploy_Central_Attach_Done')
  }

  cy.contains('mat-step-header', 'Done').parent().within(e => {
    cy.contains('button', 'Done').should('exist').and('be.enabled').scrollIntoView().click();
  });

  // we're on the managed servers page again now. verify server exists and can be sync'd.
  cy.contains('mat-expansion-panel', 'Managed Server').should('exist').and('be.visible').within(e => {
    cy.contains('button', 'Synchronize').should('exist').and('be.enabled').click();

    // don't use waitUntilContentLoaded as it does not work in within blocks.
    cy.get('mat-spinner').should('not.exist');

    cy.contains('span', 'Last sync').should('contain.text', new Date().getFullYear());
    cy.contains('td', 'flight_takeoff').should('exist'); // the aeroplane
  });

  if(screenshot) {
    cy.screenshot('BDeploy_Central_Managed_Servers_Sync')
  }
})
