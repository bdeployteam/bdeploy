//@ts-check

/**
 * Command: createGroup
 */
Cypress.Commands.add('createGroup', function (groupName, mode = 'STANDALONE') {
  cy.visitBDeploy('/', mode);
  cy.waitUntilContentLoaded();

  cy.inMainNavContent(() => {
    cy.contains('tr', groupName).should('not.exist');
    cy.pressToolbarButton('Add Instance Group');
  });

  cy.screenshot('Doc_AddGroupPanelEmpty');

  cy.inMainNavFlyin('app-add-group', () => {
    cy.contains('button', 'Save').should('exist').and('be.disabled');

    cy.fillFormInput('name', groupName);
    cy.fillFormInput('title', groupName);
    cy.fillFormInput('description', `Description of ${groupName}`);
    cy.fillImageUpload('bdeploy.png', 'image/png');
  });
  cy.screenshot('Doc_AddGroupPanelFilled');

  cy.inMainNavFlyin('app-add-group', () => {
    cy.contains('button', 'Save').should('exist').and('be.enabled').click();
  });
  cy.checkMainNavFlyinClosed();

  cy.inMainNavContent(() => {
    cy.contains('tr', groupName).should('exist');
  });
});

/**
 * Command: deleteGroup
 */
Cypress.Commands.add('deleteGroup', function (groupName, mode = 'STANDALONE') {
  cy.visitBDeploy('/', mode);
  cy.waitUntilContentLoaded();

  cy.enterGroup(groupName);

  cy.inMainNavContent(() => {
    cy.pressToolbarButton('Group Settings');
  });

  cy.inMainNavFlyin('app-settings', () => {
    cy.get(`app-bd-button[text="Delete Instance Group"]`).click();

    cy.get('app-bd-dialog-message').within(() => {
      cy.fillFormInput(undefined, groupName);
      cy.contains('button', 'Yes').should('exist').and('be.enabled').click();
    });
  });
  cy.checkMainNavFlyinClosed();

  cy.inMainNavContent(() => {
    cy.contains('tr', groupName).should('not.exist');
  });
});

/**
 * Command: uploadProductIntoGroup
 */
Cypress.Commands.add('uploadProductIntoGroup', function (groupName, fileName, screenshots = false, mode = 'STANDALONE') {
  cy.visitBDeploy('/', mode);
  cy.waitUntilContentLoaded();

  cy.enterGroup(groupName);

  cy.pressMainNavButton('Products');
  cy.get('app-products-browser').should('exist');

  if (screenshots) {
    cy.screenshot('Doc_ProductsEmpty');
  }

  cy.pressToolbarButton('Upload Product');

  if (screenshots) {
    cy.screenshot('Doc_ProductsUploadPanel');
  }

  cy.inMainNavFlyin('app-product-upload', () => {
    cy.fillFileDrop(fileName);
    cy.contains('app-bd-file-upload', `Uploading: ${fileName}`).should('not.exist');
  });

  if (screenshots) {
    cy.inMainNavContent(() => {
      // TODO: demo product name hardcoded in case of screenshots - maybe not perfect.
      cy.contains('tr', 'Demo Product').should('exist');
    });
    cy.screenshot('Doc_ProductsUploadSuccess');
  }
});

/**
 * Command: verifyProductVersion
 */
Cypress.Commands.add('verifyProductVersion', function (groupName, productName, productVersion, mode = 'STANDALONE') {
  cy.visitBDeploy('/', mode);
  cy.waitUntilContentLoaded();

  cy.enterGroup(groupName);

  cy.pressMainNavButton('Products');
  cy.get('app-products-browser').should('exist');

  cy.inMainNavContent(() => {
    const content = new RegExp(`${productName}.*${productVersion}`);
    cy.contains('tr', content).should('exist');
  });
});

/**
 * Command: enterGroup
 */
Cypress.Commands.add('enterGroup', function (groupName) {
  cy.waitUntilContentLoaded();
  cy.inMainNavContent(() => {
    cy.contains('tr', groupName).should('exist').click();
    cy.waitUntilContentLoaded();
    cy.contains('mat-toolbar', `Instances of ${groupName}`).should('exist');
  });
});

/**
 * Command: attachManaged
 */
Cypress.Commands.add('attachManaged', function (groupName) {
  // prepare MANAGED
  cy.visitBDeploy('/', 'MANAGED');
  cy.waitUntilContentLoaded();
  cy.get('app-groups-browser').should('exist');
  cy.inMainNavContent(() => {
    cy.contains('tr', groupName).should('not.exist');
  });
  cy.pressToolbarButton('Link Instance Group');
  cy.inMainNavFlyin('app-link-central', () => {
    cy.contains('mat-expansion-panel', 'Manual and Offline Linking').within(() => {
      cy.get('mat-panel-title').click();
    });

    cy.waitUntilContentLoaded();
    cy.get(`app-bd-button[text="Download Link Information"]`).within(() => {
      cy.get('button').should('exist').and('be.enabled').downloadByLinkClick('managed-ident.txt', true);
    });
  });

  // prepare CENTRAL
  cy.visitBDeploy('/', 'CENTRAL');
  cy.waitUntilContentLoaded();
  cy.get('app-groups-browser').should('exist');
  cy.inMainNavContent(() => {
    cy.contains('tr', groupName).should('exist');
  });
  cy.enterGroup(groupName);
  cy.pressMainNavButton('Managed Servers');
  cy.get('app-servers-browser').should('exist');
  cy.pressToolbarButton('Link Managed Server');
  cy.inMainNavFlyin('app-link-managed', () => {
    cy.contains('div', 'Details for server to link').should('not.exist');
    cy.contains('mat-card', 'Drop managed server information here!')
      .parent()
      .within(() => {
        cy.get('input[data-cy="managed-ident"]').attachFile({
          filePath: 'managed-ident.txt',
          mimeType: 'text/plain',
        });
      });
    cy.contains('div', 'Details for server to link').should('exist');
    cy.contains('button', 'Save').should('exist').and('be.disabled');
    cy.fillFormInput('description', 'Description of managed server');
    cy.contains('button', 'Save').should('exist').and('be.enabled').click();
  });

  cy.inMainNavContent(() => {
    cy.contains('tr', 'Description of managed server').should('exist');
  });
});

Cypress.Commands.add('cleanAllGroups', function (mode = 'STANDALONE') {
  const backend = mode === 'STANDALONE' ? 'backendBaseUrl' : mode === 'MANAGED' ? 'backendBaseUrlManaged' : 'backendBaseUrlCentral';

  cy.authenticatedRequest({ method: 'GET', url: `${Cypress.env(backend)}/group` }, mode).then((resp) => {
    if (!Array.isArray(resp.body)) {
      return;
    }
    for (const x of resp.body) {
      const group = x.name;

      cy.authenticatedRequest({ method: 'DELETE', url: `${Cypress.env(backend)}/group/${group}` }, mode);
    }
  });
});
