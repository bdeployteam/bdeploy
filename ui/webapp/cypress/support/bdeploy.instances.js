//@ts-check

/**
 * Command: createInstance
 */
Cypress.Commands.add('createInstance', function (groupName, instanceName, productName, productVersion, mode = 'STANDALONE') {
  cy.visitBDeploy('/', mode);
  cy.waitUntilContentLoaded();

  cy.enterGroup(groupName);
  cy.inMainNavContent(() => {
    cy.pressToolbarButton('Add Instance');
  });

  cy.inMainNavFlyin('app-add-instance', () => {
    cy.contains('button', 'SAVE').should('exist').and('be.disabled');

    cy.fillFormInput('name', instanceName);
    cy.fillFormInput('description', `Description of ${instanceName}`);
    cy.fillFormSelect('purpose', 'TEST');
    cy.fillFormSelect('product', productName);
    cy.fillFormSelect('version', productVersion);

    if (mode === 'CENTRAL') {
      cy.fillFormSelect('server', 'localhost');
    }

    cy.contains('button', 'SAVE').should('exist').and('be.enabled').click();
  });
  cy.checkMainNavFlyinClosed();

  cy.inMainNavContent(() => {
    cy.contains('tr', instanceName).should('exist');
  });
});

/**
 * Command: deleteInstance
 */
Cypress.Commands.add('deleteInstance', function (groupName, instanceName, mode = 'STANDALONE') {
  cy.enterInstance(groupName, instanceName, mode);

  cy.pressMainNavButton('Instance Configuration');
  cy.inMainNavContent(() => {
    cy.pressToolbarButton('Instance Settings');
  });

  cy.inMainNavFlyin('app-instance-settings', () => {
    cy.get(`app-bd-panel-button[text="Maintenance"]`).click();

    cy.get('app-bd-dialog-toolbar[header="Instance Maintenance"]').should('exist');
    cy.get(`app-bd-button[text="Delete Instance"]`).click();

    cy.contains('app-bd-dialog-message', `Delete ${instanceName}`).within(() => {
      cy.contains('button', 'YES').should('exist').and('be.enabled').click();
    });
  });
  cy.checkMainNavFlyinClosed();
  cy.inMainNavContent(() => {
    cy.contains('tr', instanceName).should('not.exist');
  });
});

Cypress.Commands.add('enterInstance', function (groupName, instanceName, mode = 'STANDALONE') {
  cy.visitBDeploy('/', mode);
  cy.waitUntilContentLoaded();

  cy.enterGroup(groupName);
  cy.inMainNavContent(() => {
    cy.contains('tr', instanceName).should('exist').click();
    cy.waitUntilContentLoaded();
    cy.contains('mat-toolbar', `Dashboard - ${instanceName}`).should('exist');
  });
});
