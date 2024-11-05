const { deleteDownloadsFolder, validateZip } = require('../support/utils');

describe('Software Repository Tests', () => {
  beforeEach(() => {
    cy.login();
    deleteDownloadsFolder();
  });

  it('Prepares Repository', () => {
    cy.visit('/');
    cy.pressMainNavButton('Software Repositories');

    cy.inMainNavContent(() => {
      cy.contains('No Repositories').should('exist');

      cy.pressToolbarButton('Add Software Repository');
    });

    // create repo and enter it
    cy.inMainNavFlyin('app-add-repository', () => {
      cy.fillFormInput('name', 'Test-Repo');
      cy.fillFormInput('description', 'Test Repository');

      cy.get('button[data-cy^="Save"]').click();
    });

    cy.checkMainNavFlyinClosed();

    cy.waitUntilContentLoaded();
    cy.screenshot('Doc_SoftwareRepo');
  });

  it('Tests Software Repos', () => {
    cy.visit('/');
    cy.pressMainNavButton('Software Repositories');

    cy.inMainNavContent(() => {
      cy.contains('tr', 'Test-Repo').should('exist').click();
      cy.contains('No products or external software').should('exist');

      cy.pressToolbarButton('Upload Software');
    });

    // upload software and product.
    cy.inMainNavFlyin('app-software-upload', () => {
      cy.fillFileDrop('external-software-hive.zip');
      cy.contains('app-bd-file-upload-raw', 'Success: external-software-hive.zip').should('exist');

      cy.fillFileDrop('external-software-2-raw-direct.zip');
      cy.contains('app-bd-file-upload-raw', 'Success: external-software-2-raw-direct.zip')
        .should('exist')
        .within(() => {
          cy.contains('Generic').should('exist');
          cy.fillFormInput('name', 'external/software/two');
          cy.fillFormInput('tag', '2.0.1');
          cy.fillFormToggle('allOs');
        });
    });

    cy.screenshot('Doc_SoftwareRepoFillInfo');

    cy.inMainNavFlyin('app-software-upload', () => {
      cy.get('button[data-cy^="Import"]').should('be.enabled').click();
      cy.contains('Import of external/software/two:2.0.1').should('exist');
    });

    cy.waitUntilContentLoaded();
    cy.screenshot('Doc_SoftwareRepoUploadSuccess');

    cy.intercept({ method: 'GET', url: '/api/softwarerepository/Test-Repo/product/list' }).as('list');

    cy.inMainNavFlyin('app-software-upload', () => {
      cy.fillFileDrop('test-product-1-direct.zip');
      cy.contains('app-bd-file-upload-raw', 'Success: test-product-1-direct.zip').should('exist');
    });

    // wait for the refresh to happen and be done.
    cy.wait('@list');

    cy.contains('tr', 'io.bdeploy/demo/product').should('exist');

    // check presence of software and product
    cy.inMainNavContent(() => {
      cy.contains('tr', 'External Software').should('exist');
      cy.contains('tr', 'Product').should('exist');

      cy.contains('tr', 'external/software/linux').should('exist');
      cy.contains('tr', 'external/software/windows').should('exist');

      cy.contains('tr', 'external/software/two')
        .should('exist')
        .within(() => {
          cy.contains('2.0.1').should('exist');
        });

      cy.contains('tr', 'io.bdeploy/demo/product')
        .should('exist')
        .within(() => {
          cy.contains('Demo Product').should('exist');
          cy.contains('1.0.0').should('exist');
        })
        .click();
    });

    cy.screenshot('Doc_SoftwareRepoDetails');

    // check product details
    cy.inMainNavFlyin('app-software-details', () => {
      cy.contains('BDeploy Team').should('exist');

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
          cy.contains('no application templates').should('exist');
        })
        .click('top');

      // "Instance Templates" panel
      cy.get(`app-bd-expand-button[data-cy="Instance Templates"]`)
        .click()
        .within(() => {
          cy.contains('no instance templates').should('exist');
        })
        .click('top');

      // "Plugins" panel
      cy.get(`app-bd-expand-button[data-cy="Plugins"]`)
        .click()
        .within(() => {
          cy.get('app-bd-no-data').should('exist');
        })
        .click('top');

      cy.get('button[data-cy^="Download"]').first().downloadByLocationAssign('product-1.0.0.zip');
      validateZip('product-1.0.0.zip', 'manifests/io.bdeploy/demo/product/1.0.0');

      cy.get('button[data-cy^="View Content"]').click();
    });

    cy.inMainNavFlyin('app-bd-bhive-browser', () => {
      cy.contains('app-bd-breadcrumbs', 'Test-Repo').should('not.exist');
      cy.contains('app-bd-breadcrumbs', 'io.bdeploy/demo/product:1.0.0').should('exist');

      cy.contains('tr', 'appTemplates').click();

      cy.get('app-bd-dialog-toolbar').within(() => {
        cy.get('button[data-cy^="Back to Parent"]').should('exist').and('be.enabled').click();
      });

      cy.contains('tr', 'product.json').click();
      cy.contains('app-bd-notification-card', 'Preview').within(() => {
        cy.get('button[data-cy^="Close"]').click();
      });

      cy.pressToolbarButton('Back to Overview');
    });

    cy.inMainNavFlyin('app-software-details', () => {
      cy.get('button[data-cy^="Delete"]').click();
      cy.contains('app-bd-notification-card', 'Delete 1.0.0').within(() => {
        cy.get('button[data-cy^="Yes"]').click();
      });
    });

    cy.inMainNavContent(() => {
      cy.contains('tr', 'Demo Product').should('not.exist');
    });

    // check external sw details.
    cy.inMainNavContent(() => {
      cy.contains('tr', 'external/software/two').click();
    });

    cy.inMainNavFlyin('app-software-details', () => {
      cy.contains('2.0.1').should('exist');
      cy.get('button[data-cy^="Delete"]').click();
      cy.contains('app-bd-notification-card', 'Delete 2.0.1').within(() => {
        cy.get('button[data-cy^="Yes"]').click();
      });
    });

    cy.inMainNavContent(() => {
      cy.contains('tr', 'external/software/two').should('not.exist');
    });
  });

  it('Tests bulk delete', () => {
    cy.visit('/');
    cy.pressMainNavButton('Software Repositories');

    // repo has only linux and windows external software packages
    cy.inMainNavContent(() => {
      cy.contains('tr', 'Test-Repo').should('exist').click();
      cy.contains('tr', 'external/software/linux').should('exist');
      cy.contains('tr', 'external/software/two').should('not.exist');
      cy.contains('tr', 'external/software/windows').should('exist');
      cy.contains('tr', 'io.bdeploy/demo/product').should('not.exist');

      cy.pressToolbarButton('Upload Software');
    });

    // upload external software two and demo product
    cy.inMainNavFlyin('app-software-upload', () => {
      cy.fillFileDrop('external-software-2-raw-direct.zip');
      cy.contains('app-bd-file-upload-raw', 'Success: external-software-2-raw-direct.zip')
        .should('exist')
        .within(() => {
          cy.contains('Generic').should('exist');
          cy.fillFormInput('name', 'external/software/two');
          cy.fillFormInput('tag', '2.0.1');
          cy.fillFormToggle('allOs');
        });
      cy.get('button[data-cy^="Import"]').should('be.enabled').click();

      cy.fillFileDrop('test-product-1-direct.zip');
      cy.contains('app-bd-file-upload-raw', 'Success: test-product-1-direct.zip').should('exist');
    });

    // repo has 3 software packages and 1 product now
    cy.inMainNavContent(() => {
      cy.contains('tr', 'external/software/linux').should('exist');
      cy.contains('tr', 'external/software/two').should('exist');
      cy.contains('tr', 'external/software/windows').should('exist');
      cy.contains('tr', 'io.bdeploy/demo/product').should('exist');
    });

    // delete 2 software packages and 1 product
    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Bulk Manipulation');

      cy.contains('tr', 'external/software/linux').find('input[type=checkbox]').check({ force: true });
      cy.contains('tr', 'external/software/two').find('input[type=checkbox]').check({ force: true });
      cy.contains('tr', 'io.bdeploy/demo/product').find('input[type=checkbox]').check({ force: true });
    });

    cy.inMainNavFlyin('app-software-details-bulk', () => {
      cy.contains('div', 'software packages/products selected.').find('strong:contains("3")').should('exist');

      cy.get('button[data-cy^=Delete]').should('be.enabled').click();

      cy.contains('app-bd-notification-card', 'Delete').within(() => {
        cy.fillFormInput('confirm', 'I UNDERSTAND');
        cy.get('button[data-cy=Yes]').click();
      });
    });

    cy.get('app-bd-bulk-operation-result-confirmation-prompt')
      .should('exist')
      .within(() => {
        cy.contains('app-bd-notification-card', 'Result').within(() => {
          cy.get('span:contains("Deleted")').should('have.length', 3);
          cy.get('button[data-cy=Close]').click();
        });
      });

    // only 1 software package remains
    cy.inMainNavContent(() => {
      cy.contains('tr', 'external/software/linux').should('not.exist');
      cy.contains('tr', 'external/software/two').should('not.exist');
      cy.contains('tr', 'external/software/windows').should('exist');
      cy.contains('tr', 'io.bdeploy/demo/product').should('not.exist');
    });
  });

  it('Cleans up', () => {
    cy.visit('/');
    cy.pressMainNavButton('Software Repositories');

    cy.inMainNavContent(() => {
      cy.contains('tr', 'Test-Repo').should('exist').click();
    });

    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Repository Settings');
    });

    // open permission dialog just for a screenshot.
    cy.inMainNavFlyin('app-settings', () => {
      cy.get('button[data-cy~="Permissions"]').click();
    });

    cy.waitUntilContentLoaded();
    cy.screenshot('Doc_SoftwareRepoPermissions');

    cy.inMainNavFlyin('app-software-repository-permissions', () => {
      cy.pressToolbarButton('Back');
    });

    // delete repo and check
    cy.inMainNavFlyin('app-settings', () => {
      cy.get('button[data-cy^="Delete"]').click();

      cy.contains('app-bd-notification-card', 'Delete').within(() => {
        cy.fillFormInput('confirm', 'Test-Repo');
        cy.get('button[data-cy^="Yes"]').should('be.enabled').click();
      });
    });

    cy.checkMainNavFlyinClosed();

    cy.inMainNavContent(() => {
      cy.contains('No Repositories').should('exist');
    });
  });
});
