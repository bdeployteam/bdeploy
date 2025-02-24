//@ts-check

Cypress.Commands.add('createGroup', function (groupName) {
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

Cypress.Commands.add('deleteGroup', function (groupName) {
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

Cypress.Commands.add('uploadProductIntoGroup', function (groupName, fileName, screenshots = false) {
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
    cy.contains('app-bd-file-upload', 'Success').should('exist');
  });

  if (screenshots) {
    cy.inMainNavContent(() => {
      // TODO: demo product name hardcoded in case of screenshots - maybe not perfect.
      cy.contains('tr', 'Demo Product').should('exist');
    });
    cy.screenshot('Doc_ProductsUploadSuccess');
  }
});

Cypress.Commands.add('verifyProductVersion', function (groupName, productName, productVersion) {
  cy.enterGroup(groupName);

  cy.pressMainNavButton('Products');
  cy.get('app-products-browser').should('exist');

  cy.inMainNavContent(() => {
    const content = new RegExp(`${productName}.*${productVersion}`);
    cy.contains('tr', content).should('exist');
  });
});

Cypress.Commands.add('enterGroup', function (groupName) {
  cy.inMainNavContent(() => {
    cy.contains('tr', groupName).should('exist').click();
    cy.contains('mat-toolbar', groupName).should('exist');
  });
});

Cypress.Commands.add('attachManagedSide', function (groupName) {
  // prepare MANAGED
  cy.screenshot('Doc_ManagedEmpty');
  cy.get('app-groups-browser').should('exist');
  cy.inMainNavContent(() => {
    cy.contains('tr', groupName).should('not.exist');
  });
  cy.pressToolbarButton('Link Instance Group');
  cy.waitUntilContentLoaded();
  cy.screenshot('Doc_ManagedLinkGroup');
  cy.inMainNavFlyin('app-link-central', () => {
    cy.contains('mat-expansion-panel', 'Manual and Offline Linking').within(() => {
      cy.get('mat-panel-title').click();
    });

    cy.get(`app-bd-button[text="Download Link Information"]`).within(() => {
      cy.get('button').should('exist').and('be.enabled').downloadByLinkClick('managed-ident.txt', true);
    });
  });
});

Cypress.Commands.add('attachCentralSide', function (groupName) {
  // prepare CENTRAL
  cy.enterGroup(groupName);
  cy.pressMainNavButton('Managed Servers');
  cy.get('app-servers-browser').should('exist');
  cy.screenshot('Doc_CentralEmptyServers');
  cy.pressToolbarButton('Link Managed Server');
  cy.screenshot('Doc_CentralLinkServer');
  cy.inMainNavFlyin('app-link-managed', () => {
    cy.contains('div', 'Details for server to link').should('not.exist');
    cy.contains('app-bd-file-drop', 'Drop managed server information here')
      .parent()
      .within(() => {
        cy.get('input[data-cy="managed-ident"]').selectFile(
          { contents: Cypress.config('fixturesFolder') + '/managed-ident.txt' },
          { force: true },
        );
      });
    cy.contains('div', 'Details for server to link').should('exist');
    cy.contains('button', 'Save').should('exist').and('be.disabled');
    cy.fillFormInput('description', 'Description of managed server');
    cy.fillFormInput('uri', Cypress.env('backendBaseUrlManaged')); // DON'T use baseUrlManaged, due to direct backend/backend connection!
  });
  cy.screenshot('Doc_CentralLinkServerFilled');
  cy.inMainNavFlyin('app-link-managed', () => {
    cy.contains('button', 'Save').should('exist').and('be.enabled').click();
  });

  cy.inMainNavContent(() => {
    cy.contains('tr', 'Description of managed server').should('exist');
  });

  cy.screenshot('Doc_CentralLinkDone');
});

Cypress.Commands.add('cleanAllGroups', function (mode = 'STANDALONE') {
  const backend =
    mode === 'STANDALONE'
      ? Cypress.config('baseUrl')
      : mode === 'MANAGED'
        ? Cypress.env('baseUrlManaged')
        : Cypress.env('baseUrlCentral');

  cy.authenticatedRequest({ method: 'GET', url: `${backend}/api/group` }, mode).then((resp) => {
    if (!Array.isArray(resp.body)) {
      return;
    }
    for (const x of resp.body) {
      const group = x.instanceGroupConfiguration.name;

      cy.authenticatedRequest({ method: 'DELETE', url: `${backend}/api/group/${group}` }, mode);
    }
  });
});

Cypress.Commands.add('cleanAllSoftwareRepos', function (mode = 'STANDALONE') {
  const backend =
    mode === 'STANDALONE'
      ? Cypress.config('baseUrl')
      : mode === 'MANAGED'
        ? Cypress.env('baseUrlManaged')
        : Cypress.env('baseUrlCentral');

  cy.authenticatedRequest({ method: 'GET', url: `${backend}/api/softwarerepository` }, mode).then((resp) => {
    if (!Array.isArray(resp.body)) {
      return;
    }
    for (const x of resp.body) {
      const softwareRepository = x.name;

      cy.authenticatedRequest(
        {
          method: 'DELETE',
          url: `${backend}/api/softwarerepository/${softwareRepository}`,
        },
        mode,
      );
    }
  });
});
