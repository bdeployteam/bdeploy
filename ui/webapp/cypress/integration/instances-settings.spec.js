//@ts-check

describe('Instance Settings Tests', () => {
  var groupName = 'Demo';
  var instanceName = 'TestInstance';

  beforeEach(() => {
    cy.login();
  });

  it('Prepares the test (group, products, instance)', () => {
    cy.visit('/');
    cy.createGroup(groupName);
    cy.uploadProductIntoGroup(groupName, 'test-product-1-direct.zip');
    cy.createInstance(groupName, instanceName, 'Demo Product', '1.0.0');
  });

  it('Tests Base Configuration', () => {
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Instance Configuration');

    // Add Server Process
    cy.inMainNavContent(() => {
      cy.get('app-configuration').should('exist');
      cy.contains('mat-toolbar', `Configuration - ${instanceName}`).should('exist');

      cy.pressToolbarButton('Instance Settings');
    });

    cy.inMainNavFlyin('app-instance-settings', () => {
      cy.get('button[data-cy="Base Configuration"]').click();
    });

    cy.inMainNavFlyin('app-edit-config', () => {
      cy.fillFormInput('name', `${instanceName} (*)`);
      cy.fillFormInput('description', `${instanceName} (*)`);
      cy.fillFormSelect('purpose', 'DEVELOPMENT');
      cy.get('button[data-cy="APPLY"]').click();
    });

    cy.inMainNavContent(() => {
      cy.contains('mat-toolbar', `Configuration - ${instanceName} (*)`).should('exist');
      cy.pressToolbarButton('Local Changes');
    });

    cy.inMainNavFlyin('app-local-changes', () => {
      cy.get('button[data-cy^="Discard"]').click();
      cy.contains('app-bd-notification-card', 'Discard').within(() => {
        cy.get('button[data-cy="YES"]').click();
      });

      cy.get('button[data-cy="Close"]').click();
    });

    cy.checkMainNavFlyinClosed();

    cy.inMainNavContent(() => {
      cy.contains('mat-toolbar', `Configuration - ${instanceName} (*)`).should('not.exist');
      cy.contains('mat-toolbar', `Configuration - ${instanceName}`).should('exist');
    });
  });

  it('Cleans up', () => {
    cy.deleteGroup(groupName);
  });
});
