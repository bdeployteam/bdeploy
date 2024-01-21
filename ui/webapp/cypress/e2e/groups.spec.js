//@ts-check

const { validateZip, deleteDownloadsFolder } = require('../support/utils');

describe('Groups Tests', () => {
  var groupName = 'Demo';
  var instanceName = 'TestInstance';

  beforeEach(() => {
    cy.login();
    deleteDownloadsFolder();
  });

  it('Creates a group', () => {
    cy.visit('/');
    cy.createGroup(groupName);

    cy.screenshot('Doc_SearchBarEnabled', {
      clip: { x: 0, y: 0, height: 80, width: 1280 },
    });
    cy.screenshot('Doc_DemoGroup');

    cy.visit('/');
    cy.enterGroup(groupName);
    cy.waitUntilContentLoaded();
    cy.screenshot('Doc_DemoInstancesEmpty');
  });

  it('Switches to card mode', () => {
    cy.visit('/');

    cy.inMainNavContent(() => {
      cy.contains('tr', groupName).should('exist');
      cy.contains('app-bd-data-card', groupName).should('not.exist');
    });

    cy.screenshot('Doc_ModeTable');
    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Toggle Card Mode');

      cy.contains('tr', groupName).should('not.exist');
      cy.contains('app-bd-data-card', groupName).should('exist');
    });

    cy.screenshot('Doc_ModeCards');
    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Toggle Card Mode');

      cy.contains('tr', groupName).should('exist');
      cy.contains('app-bd-data-card', groupName).should('not.exist');
    });
  });

  it('Edits the group', () => {
    cy.visit('/');
    cy.enterGroup(groupName);

    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Group Settings');
    });

    cy.inMainNavFlyin('app-settings', () => {
      cy.get(`app-bd-panel-button[text="Edit Instance Group..."]`).click();
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
    cy.visit('/');
    cy.uploadProductIntoGroup(groupName, 'test-product-2-direct.zip', true); // this first for accurate screenshots :)

    cy.visit('/');
    cy.uploadProductIntoGroup(groupName, 'test-product-1-direct.zip');

    cy.visit('/');
    cy.verifyProductVersion(groupName, 'Demo Product', '1.0.0');

    cy.visit('/');
    cy.verifyProductVersion(groupName, 'Demo Product', '2.0.0');
  });

  it("Checks the product's details panel", function () {
    cy.visit('/');
    cy.enterGroup(groupName);

    cy.pressMainNavButton('Products');
    cy.get('app-products-browser').should('exist');

    // open details of Demo Product 2.0.0
    cy.inMainNavContent(() => {
      cy.contains('tr', /Demo Product.*2.0.0/)
        .should('exist')
        .click();
    });

    cy.waitUntilContentLoaded();
    cy.screenshot('Doc_ProductDetailsPanel');

    cy.inMainNavFlyin('app-product-details', () => {
      // check product is unused
      cy.get('app-bd-no-data').should('exist');

      // "Labels" panel
      cy.get(`app-bd-expand-button[data-cy="Labels"]`)
        .click()
        .within(() => {
          cy.contains('tr', /X-Product.*io.bdeploy\/demo/).should('exist');
        })
        .click('top');

      // "Application Templates" panel
      cy.get(`app-bd-expand-button[data-cy="Application Templates"]`)
        .click()
        .within(() => {
          cy.contains('tr', 'Server With Sleep').should('exist');
        })
        .click('top');

      // "Instance Templates" panel
      cy.get(`app-bd-expand-button[data-cy="Instance Templates"]`)
        .click()
        .within(() => {
          cy.contains('tr', 'Default Configuration').should('exist');
        })
        .click('top');

      // "Plugins" panel
      cy.get(`app-bd-expand-button[data-cy="Plugins"]`)
        .click()
        .within(() => {
          cy.get('app-bd-no-data').should('exist');
        })
        .click('top');

      cy.get('button[data-cy^="Download"]').downloadByLocationAssign('product-2.0.0.zip');
      validateZip('product-2.0.0.zip', 'manifests/io.bdeploy/demo/product/2.0.0');
    });

    cy.inMainNavFlyin('app-product-details', () => {
      // "Create new Instance" button
      cy.get(`app-bd-button[text="Create new Instance..."]`).click();
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

    cy.screenshot('Doc_InstanceAdd');

    // ...go on checking product details stuff
    cy.pressMainNavButton('Products');
    cy.get('app-products-browser').should('exist');

    cy.intercept({
      method: 'GET',
      url: `/api/group/${groupName}/product/io.bdeploy/demo/product/2.0.0/usedIn`,
    }).as('getUsage');

    cy.inMainNavContent(() => {
      cy.contains('tr', /Demo Product.*2.0.0/)
        .should('exist')
        .click();
    });

    cy.wait('@getUsage');

    cy.inMainNavFlyin('app-product-details', () => {
      // "Delete" button
      cy.get('button[data-cy="Delete"]').should('be.enabled').click();

      cy.get('app-bd-notification-card').within(() => {
        cy.get('button[data-cy="Yes"]').should('exist').and('be.enabled').click();
      });
    });
    cy.checkMainNavFlyinClosed();

    cy.inMainNavContent(() => {
      cy.contains('tr', /Demo Product.*2.0.0/).should('not.exist');
    });

    cy.visit('/');
    cy.uploadProductIntoGroup(groupName, 'test-product-2-direct.zip');

    cy.visit('/');
    cy.verifyProductVersion(groupName, 'Demo Product', '2.0.0');
  });

  it('Creates an instance', () => {
    cy.visit('/');
    cy.enterGroup(groupName);

    cy.waitUntilContentLoaded();
    cy.screenshot('Doc_DemoInstancesNoInstance');

    cy.visit('/');
    cy.createInstance(groupName, instanceName, 'Demo Product', '1.0.0');

    cy.screenshot('Doc_DemoInstance');
  });

  it('Deletes the instance', () => {
    cy.visit('/');
    cy.deleteInstance(groupName, instanceName);
  });

  it('Tests the maintenance functions', () => {
    cy.visit('/');
    cy.enterGroup(groupName);

    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Group Settings');
    });

    cy.inMainNavFlyin('app-settings', () => {
      cy.get(`app-bd-button[text="Repair BHive Problems and Prune"]`).click();
      cy.contains('app-bd-notification-card', 'Repair and Prune').within(() => {
        cy.get('button[data-cy^="Yes"]').click();
      });

      cy.get('app-bd-dialog-message').within(() => {
        cy.contains('No damaged objects were found.').should('exist');
        cy.contains('Prune freed').should('exist');
        cy.contains('button', 'OK').should('exist').and('be.enabled').click();
      });

      cy.pressToolbarButton('Close');
    });
  });

  it('Deletes the group', () => {
    cy.visit('/');
    cy.deleteGroup(groupName);
  });
});
