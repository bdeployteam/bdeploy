/**
 * Command: createGroup
 */
Cypress.Commands.add('createGroup', function (groupName, mode = 'STANDALONE') {
  cy.visitBDeploy('/', mode);
  cy.waitUntilContentLoaded();

  cy.inMainNavContent(() => {
    cy.contains('tr', groupName).should('not.exist');
    cy.pressToolbarPanelButton('Add Instance Group');
  });

  cy.inMainNavFlyin('Add Instance Group', () => {
    cy.contains('button', 'SAVE').should('exist').and('be.disabled');

    cy.fillFormInput('name', groupName);
    cy.fillFormInput('title', groupName);
    cy.fillFormInput('description', `Description of ${groupName}`);
    cy.fillImageUpload('bdeploy.png', 'image/png');

    cy.contains('button', 'SAVE').should('exist').and('be.enabled').click();
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
    cy.pressToolbarPanelButton('Instance Group Settings');
  });

  cy.inMainNavFlyin('Instance Group Settings', () => {
    cy.get(`app-bd-panel-button[text="Maintenance"]`).click();

    cy.get('app-bd-dialog-toolbar[header="Instance Group Maintenance"]').should('exist');
    cy.get(`app-bd-button[text="Delete Instance Group"]`).click();

    cy.get('app-bd-dialog-message').within(() => {
      cy.fillFormInput(undefined, groupName);
      cy.contains('button', 'YES').should('exist').and('be.enabled').click();
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
Cypress.Commands.add('uploadProductIntoGroup', function (groupName, fileName, mode = 'STANDALONE') {
  cy.visitBDeploy('/', mode);
  cy.waitUntilContentLoaded();

  cy.enterGroup(groupName);

  cy.pressMainNavButton('Products');
  cy.get('app-products-browser').should('exist');

  cy.pressToolbarPanelButton('Upload Product');
  cy.inMainNavFlyin('Upload', () => {
    cy.fillFileDrop(fileName);
    cy.contains('app-bd-file-upload', `Uploading: ${fileName}`).should('not.exist');
  });
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
 * Command: createInstance
 */
Cypress.Commands.add('createInstance', function (groupName, instanceName, productName, productVersion, mode = 'STANDALONE') {
  cy.visitBDeploy('/', mode);
  cy.waitUntilContentLoaded();

  cy.enterGroup(groupName);
  cy.inMainNavContent(() => {
    cy.pressToolbarPanelButton('Add Instance');
  });

  cy.inMainNavFlyin('Add Instance', () => {
    cy.contains('button', 'SAVE').should('exist').and('be.disabled');

    cy.fillFormInput('name', instanceName);
    cy.fillFormInput('description', `Description of ${instanceName}`);
    cy.fillFormSelect('purpose', 'TEST');
    cy.fillFormSelect('product', productName);
    cy.fillFormSelect('version', productVersion);

    cy.contains('button', 'SAVE').should('exist').and('be.enabled').click();
  });
  cy.checkMainNavFlyinClosed();

  cy.inMainNavContent(() => {
    cy.contains('tr', instanceName).should('exist');
  });
});

/**
 * Command: enterGroup
 */
Cypress.Commands.add('enterGroup', function (groupName) {
  cy.inMainNavContent(() => {
    cy.contains('tr', groupName).should('exist').click();
    cy.contains('mat-toolbar', `Instances of ${groupName}`).should('exist');
  });
});
