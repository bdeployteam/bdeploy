//@ts-check

const { validateZip, deleteDownloadsFolder } = require('../support/utils');

describe('Groups Tests', () => {
  var groupName = 'Demo';
  var instanceName = 'TestInstance';

  before(() => {
    cy.cleanAllGroups();
  });

  beforeEach(() => {
    cy.login();
    deleteDownloadsFolder();
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

  it("Checks the product's details panel", function () {
    cy.visit('/');
    cy.waitUntilContentLoaded();

    cy.enterGroup(groupName);

    cy.pressMainNavButton('Products');
    cy.get('app-products-browser').should('exist');

    // open details of Demo Product 2.0.0
    cy.inMainNavContent(() => {
      cy.contains('tr', /Demo Product.*2.0.0/)
        .should('exist')
        .click();
    });

    cy.inMainNavFlyin('app-product-details', () => {
      // check product is unused
      cy.get('app-bd-no-data').should('exist');

      // "Labels" panel
      cy.get(`app-bd-panel-button[text="Labels"]`).click();
      cy.get('app-bd-dialog-toolbar[header="Details"]').should('exist');
      cy.contains('tr', /X-Product.*io.bdeploy\/demo/).should('exist');
      cy.pressToolbarButton('Back to Overview');

      // "Application Templates" panel
      cy.get(`app-bd-panel-button[text="Application Templates"]`).click();
      cy.get('app-bd-dialog-toolbar[header="Details"]').should('exist');
      cy.contains('tr', 'Server With Sleep').should('exist');
      cy.pressToolbarButton('Back to Overview');

      // "Instance Templates" panel
      cy.get(`app-bd-panel-button[text="Instance Templates"]`).click();
      cy.get('app-bd-dialog-toolbar[header="Details"]').should('exist');
      cy.contains('tr', 'Default Configuration').should('exist');
      cy.pressToolbarButton('Back to Overview');

      // "Plugins" panel
      cy.get(`app-bd-panel-button[text="Plugins"]`).click();
      cy.get('app-bd-dialog-toolbar[header="Details"]').should('exist');
      cy.get('app-bd-no-data').should('exist');
      cy.pressToolbarButton('Back to Overview');

      cy.get('button[data-cy^="Download"]').downloadByLocationAssign('product-2.0.0.zip');
      validateZip('product-2.0.0.zip', 'manifests/io.bdeploy/demo/product/2.0.0');
    });

    cy.inMainNavFlyin('app-product-details', () => {
      // "Create new Instance" button
      cy.get(`app-bd-button[text="Create new Instance"]`).click();
    });

    // "Create new Instance" switches back to the Instance Group dialog...
    cy.inMainNavContent(() => {
      cy.contains('mat-toolbar', `Instances of ${groupName}`).should('exist');
    });
    // ...with opened "Add Instance" flyin
    cy.inMainNavFlyin('app-add-instance', () => {
      cy.contains('app-bd-form-select[name="product"]', 'Demo Product').should('exist');
      cy.contains('app-bd-form-select[name="version"]', '2.0.0').should('exist');
    });

    // ...go on checking product details stuff
    cy.pressMainNavButton('Products');
    cy.get('app-products-browser').should('exist');
    cy.inMainNavContent(() => {
      cy.contains('tr', /Demo Product.*2.0.0/)
        .should('exist')
        .click();
    });

    cy.inMainNavFlyin('app-product-details', () => {
      // "Delete" button
      cy.get(`app-bd-button[text="Delete"]`).click();

      cy.get('app-bd-notification-card').within(() => {
        cy.get('button[data-cy="YES"]').should('exist').and('be.enabled').click();
      });
    });
    cy.checkMainNavFlyinClosed();

    cy.inMainNavContent(() => {
      cy.contains('tr', /Demo Product.*2.0.0/).should('not.exist');
    });

    cy.uploadProductIntoGroup(groupName, 'test-product-2-direct.zip');
    cy.verifyProductVersion(groupName, 'Demo Product', '2.0.0');
  });

  it('Creates an instance', () => {
    cy.createInstance(groupName, instanceName, 'Demo Product', '1.0.0');
  });

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
