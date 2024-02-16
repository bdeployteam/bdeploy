//@ts-check

/**
 * Command: createInstance
 */
Cypress.Commands.add('createInstance', function (groupName, instanceName, productName, productVersion) {
  cy.waitUntilContentLoaded();

  cy.enterGroup(groupName);
  cy.inMainNavContent(() => {
    cy.pressToolbarButton('Add Instance');
  });

  cy.inMainNavFlyin('app-add-instance', () => {
    cy.contains('button', 'Save').should('exist').and('be.disabled');

    cy.fillFormInput('name', instanceName);
    cy.fillFormInput('description', `Description of ${instanceName}`);
    cy.fillFormSelect('purpose', 'TEST');
    cy.fillFormSelect('product', productName);
    cy.fillFormSelect('version', productVersion);

    // only on central, fill the managed server select.
    cy.document()
      .its('body')
      .within((body) => {
        const el = body.querySelectorAll('app-bd-form-select[name="server"]');
        if (el?.length) {
          cy.fillFormSelect('server', 'localhost');
        }
      });

    cy.contains('button', 'Save').should('exist').and('be.enabled').click();
  });
  cy.checkMainNavFlyinClosed();

  cy.inMainNavContent(() => {
    cy.contains('Configuration').should('exist');
  });
});

/**
 * Command: deleteInstance
 */
Cypress.Commands.add('deleteInstance', function (groupName, instanceName) {
  cy.enterInstance(groupName, instanceName);

  cy.pressMainNavButton('Instance Configuration');

  cy.inMainNavFlyin('app-instance-settings', () => {
    cy.get(`app-bd-button[text="Delete Instance"]`).click();

    cy.contains('app-bd-dialog-message', `Delete ${instanceName}`).within(() => {
      cy.contains('button', 'Yes').should('exist').and('be.enabled').click();
    });
  });
  cy.checkMainNavFlyinClosed();
  cy.inMainNavContent(() => {
    cy.contains('tr', instanceName).should('not.exist');
  });
});

Cypress.Commands.add('enterInstance', function (groupName, instanceName) {
  cy.waitUntilContentLoaded();

  cy.enterGroup(groupName);

  cy.inMainNavContent(() => {
    cy.contains('tr', instanceName).should('exist').click();
    cy.waitUntilContentLoaded();
    cy.contains('mat-toolbar', `Dashboard`).should('exist');
  });
});
