describe('Instance Group Product Import Test', () => {
  var groupName = 'Demo';
  var instanceName = 'Demo Instance';
  var softwareRepoName = 'Test-Repo';
  var productName = 'Demo Product';
  var productVersion = '1.0.0';

  beforeEach(() => {
    cy.login();
  });

  it('Prepares repository', () => {
    cy.visit('/');
    cy.pressMainNavButton('Software Repositories');

    cy.inMainNavContent(() => {
      cy.contains('No Repositories').should('exist');

      cy.pressToolbarButton('Add Software Repository');
    });

    // create repo and enter it
    cy.inMainNavFlyin('app-add-repository', () => {
      cy.fillFormInput('name', softwareRepoName);
      cy.fillFormInput('description', 'Test Repository');

      cy.get('button[data-cy^="Save"]').click();
    });

    cy.checkMainNavFlyinClosed();
  });

  it('Uploads product', () => {
    cy.visit('/');
    cy.pressMainNavButton('Software Repositories');

    cy.inMainNavContent(() => {
      cy.contains('tr', softwareRepoName).should('exist').click();
      cy.contains('No products or external software').should('exist');

      cy.pressToolbarButton('Upload Software');
    });

    // upload product.
    cy.inMainNavFlyin('app-software-upload', () => {
      cy.fillFileDrop('test-product-1-direct.zip');
      cy.contains('app-bd-file-upload-raw', 'Success: test-product-1-direct.zip').should('exist');
    });

    cy.contains('tr', 'io.bdeploy/demo/product').should('exist');

    // verify upload
    cy.inMainNavContent(() => {
      cy.contains('tr', 'Product').should('exist');

      cy.contains('tr', 'io.bdeploy/demo/product')
        .should('exist')
        .within(() => {
          cy.contains(productName).should('exist');
          cy.contains(productVersion).should('exist');
        })
        .click();
    });

    cy.screenshot('Doc_SoftwareRepoProductUploaded');
  });

  it('Creates a group', () => {
    cy.visit('/');
    cy.createGroup(groupName);
  });

  it('Imports product', () => {
    cy.visit('/');
    cy.inMainNavContent(() => {
      cy.contains('tr', groupName).should('exist').click();
    });
    cy.pressMainNavButton('Products');
    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Import Product...');
    });

    cy.waitUntilContentLoaded();
    cy.screenshot('Doc_ImportProduct_SelectRepo');

    cy.inMainNavFlyin('app-product-transfer-repo', () => {
      cy.fillFormSelect('repository', softwareRepoName);
    });
    cy.screenshot('Doc_ImportProduct_SelectProduct');
    cy.inMainNavFlyin('app-product-transfer-repo', () => {
      cy.fillFormSelect('product', productName);
    });
    cy.screenshot('Doc_ImportProduct_SelectVersion');
    cy.inMainNavFlyin('app-product-transfer-repo', () => {
      cy.contains('mat-checkbox', productVersion).should('exist').click();
    });
    cy.inMainNavFlyin('app-product-transfer-repo', () => {
      cy.contains('button', 'Import').should('exist').and('be.enabled').click();
    });

    cy.visit('/');
    cy.verifyProductVersion(groupName, productName, productVersion);
    cy.screenshot('Doc_ImportProduct_Success');
  });

  it('Create instance with imported product', () => {
    cy.visit('/');
    cy.createInstance(groupName, instanceName, productName, productVersion);
  });
});
