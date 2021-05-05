describe('Central/Managed Basic Test', function () {
  var groupName = 'Demo';
  var instanceName = 'TestInstance';
  var instanceName2 = 'TestInstance2';

  it('Creates a group on the central server', () => {
    cy.visitBDeploy('/', 'CENTRAL');
    cy.createGroup(groupName, 'CENTRAL');
  });

  it('Attaches a managed server to the central server', () => {
    cy.attachManaged(groupName);
  });

  it('Deletes and re-attaches the managed server to the central server', () => {
    cy.deleteGroup(groupName, 'MANAGED');

    cy.visitBDeploy('/', 'CENTRAL');
    // go to managed servers and sync -> fails
    cy.enterGroup(groupName);
    cy.pressMainNavButton('Managed Servers');
    cy.get('app-servers-browser').should('exist');
    cy.inMainNavContent(() => {
      cy.get('app-bd-server-sync-button').should('have.length', 1).click();
      cy.checkAndConfirmSnackbar('was not found'); // Unfortunately, <url> was not found...
    });
    // back in groups browser -> re-enter managed servers
    cy.get('app-groups-browser').should('exist');
    cy.enterGroup(groupName);
    cy.pressMainNavButton('Managed Servers');
    cy.get('app-servers-browser').should('exist');
    cy.inMainNavContent(() => {
      cy.contains('tr', 'Description of managed server').should('have.length', 1).click();
    });
    cy.inMainNavFlyin('app-server-details', () => {
      cy.get(`app-bd-button[text="Delete"]`).click();

      cy.get('app-bd-dialog-message').within(() => {
        cy.contains('button', 'YES').should('exist').and('be.enabled').click();
      });
    });

    cy.attachManaged(groupName);
  });

  it('Creates an instance on managed server', () => {
    cy.uploadProductIntoGroup(groupName, 'test-product-1-direct.zip', 'MANAGED');

    cy.createInstance(groupName, instanceName, 'Demo Product', '1.0.0', 'MANAGED');

    cy.visitBDeploy('/', 'CENTRAL');
    cy.waitUntilContentLoaded();
    cy.enterGroup(groupName);
    cy.inMainNavContent(() => {
      cy.contains('tr', instanceName).should('not.exist');
    });

    cy.pressMainNavButton('Managed Servers');
    cy.get('app-servers-browser').should('exist');
    cy.inMainNavContent(() => {
      cy.get('app-bd-server-sync-button').should('have.length', 1).click();
    });

    cy.pressMainNavButton('Instances');
    cy.inMainNavContent(() => {
      cy.contains('tr', instanceName).should('exist');
    });

    // TODO: Product is now missing on Central -> check N/A (currently missing on UI)
  });

  // TODO sync product von managed to central

  it('Creates an instance on central server', () => {
    cy.uploadProductIntoGroup(groupName, 'test-product-2-direct.zip', 'CENTRAL');
    cy.createInstance(groupName, instanceName2, 'Demo Product', '2.0.0', 'CENTRAL');

    cy.visitBDeploy('/', 'MANAGED');
    cy.waitUntilContentLoaded();
    cy.enterGroup(groupName);
    cy.inMainNavContent(() => {
      cy.contains('tr', instanceName2).should('exist');
    });
  });

  // TODO config instance on managed and sync to central

  // TODO install, activate on central (requires instance configuration)

  // TODO check product synced to managed

  // TODO process control from central and managed (requires instance configuration)

  it('Deletes the group on central and managed server', () => {
    cy.deleteGroup(groupName, 'CENTRAL');

    cy.visitBDeploy('/', 'MANAGED');
    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.contains('tr', groupName).should('exist');
    });

    cy.deleteGroup(groupName, 'MANAGED');
  });
});
