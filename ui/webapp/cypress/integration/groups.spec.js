describe('Groups Tests', () => {
  var groupName = 'Demo';
  var instanceName = 'TestInstance';

  beforeEach(() => {
    cy.login();
  });

  it('Creates a group', () => {
    cy.visit('/');
    cy.createGroup(groupName);
  });

  it('Switches to card mode', () => {
    cy.visit('/');

    cy.inMainNavContent(() => {
      cy.contains('tr', groupName).should('exist');
      cy.contains('app-bd-data-card', groupName).should('not.exist');

      cy.pressToolbarButton('Toggle Card Mode');

      cy.contains('tr', groupName).should('not.exist');
      cy.contains('app-bd-data-card', groupName).should('exist');

      cy.pressToolbarButton('Toggle Card Mode');

      cy.contains('tr', groupName).should('exist');
      cy.contains('app-bd-data-card', groupName).should('not.exist');
    });
  });

  it('Edits the group', () => {
    cy.visit('/');

    cy.enterGroup(groupName);
    cy.pressMainNavButton('Group Settings');

    cy.inMainNavFlyin('app-settings', () => {
      cy.get(`app-bd-panel-button[text="Edit Instance Group"]`).click();
      cy.get('app-bd-dialog-toolbar[header="Edit Instance Group"]').should('exist');

      cy.fillFormInput('description', `Description of ${instanceName}`);
    });
  });

  it('Searches in groups', () => {
    cy.visit('/');

    // group visible?
    cy.inMainNavContent(() => {
      cy.contains('tr', groupName).should('exist');
    });

    // search the existing group -- group listed in table?
    cy.get('app-main-nav-top').within(() => {
      cy.get('input').click().type(`{selectall}${groupName}`);
    });
    cy.inMainNavContent(() => {
      cy.contains('tr', groupName).should('exist');
    });

    // search a non-existing group ('DemoX')-- table row disappeared?
    cy.get('app-main-nav-top').within(() => {
      cy.get('input').click().type('{end}X');
    });
    cy.inMainNavContent(() => {
      cy.contains('tr', groupName).should('not.exist');
    });

    // clear search field -- group in table appears again?
    cy.get('app-main-nav-top').within(() => {
      cy.get('input').click().type('{selectall}{backspace}');
    });
    cy.inMainNavContent(() => {
      cy.contains('tr', groupName).should('exist');
    });
  });

  it('Upload products to the instance group', function () {
    cy.uploadProductIntoGroup(groupName, 'test-product-1-direct.zip');
    cy.uploadProductIntoGroup(groupName, 'test-product-2-direct.zip');
    cy.verifyProductVersion(groupName, 'Demo Product', '1.0.0');
    cy.verifyProductVersion(groupName, 'Demo Product', '2.0.0');
  });

  it('Creates an instance', () => {
    cy.createInstance(groupName, instanceName, 'Demo Product', '1.0.0');
  });

  // Permission tests:
  // TODO create user with global permissions / ensure that these user exist
  // TODO check global permissions on instance group
  // TODO grant/revoke admin and write permissions to globalRead user
  // TODO add a user
  // TODO remove a user
  // TODO cleanup users

  it('Deletes the instance', () => {
    cy.deleteInstance(groupName, instanceName);
  });

  it('Tests the maintenance functions', () => {
    cy.visit('/');

    cy.enterGroup(groupName);
    cy.pressMainNavButton('Group Settings');

    cy.inMainNavFlyin('app-settings', () => {
      cy.get(`app-bd-panel-button[text="Maintenance"]`).click();

      cy.get(`app-bd-button[text="Repair BHive Problems"]`).click();

      cy.get('app-bd-dialog-message').within(() => {
        cy.contains('app-bd-dynamic', 'No damaged objects were found.').should('exist');
        cy.contains('button', 'OK').should('exist').and('be.enabled').click();
      });

      cy.get(`app-bd-button[text="Prune unused data in BHive"]`).click();

      cy.get('app-bd-dialog-message').within(() => {
        cy.contains('app-bd-dynamic', 'Prune').should('exist');
        cy.contains('button', 'OK').should('exist').and('be.enabled').click();
      });

      cy.pressToolbarButton('Back to Overview');
      cy.pressToolbarButton('Close');
    });
  });

  it('Deletes the group', () => {
    cy.deleteGroup(groupName);
  });
});
