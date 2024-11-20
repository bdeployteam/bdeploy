describe('Report UI Tests', () => {
  const groupA = 'DemoGroupA';
  const groupB = 'DemoGroupB';

  const instanceA = 'InstanceA';
  const instanceB = 'InstanceB';
  const instanceC = 'InstanceC';

  beforeEach(() => {
    cy.login();
  });

  it('Prepares groups and instances', () => {
    cy.visit('/');
    cy.createGroup(groupA);

    cy.visit('/');
    cy.uploadProductIntoGroup(groupA, 'test-product-1-direct.zip');

    cy.visit('/');
    cy.createInstance(groupA, instanceA, 'Demo Product', '1.0.0');

    cy.visit('/');
    cy.createGroup(groupB);

    cy.visit('/');
    cy.uploadProductIntoGroup(groupB, 'chat-product-1-direct.zip');

    cy.visit('/');
    cy.uploadProductIntoGroup(groupB, 'test-product-2-direct.zip');

    cy.visit('/');
    cy.createInstance(groupB, instanceB, 'Demo Chat App', '1.0.0');

    cy.visit('/');
    cy.createInstance(groupB, instanceC, 'Demo Product', '2.0.0');
  });

  it('Checks that report permission exists', () => {
    cy.visit('/');
    cy.get('button[data-cy=Administration]').click();
    cy.contains('a', 'User Accounts').click();
    cy.inMainNavContent(() => {
      cy.contains('tr', 'admin').should('exist').click();
    });
    cy.inMainNavFlyin('app-user-admin-detail', () => {
      cy.get('button[data-cy^="Assign Permission"]').click();
    });
    cy.inMainNavFlyin('app-user-assign-permission', () => {
      cy.fillFormSelect('scope', 'Report: Products In Use');
      cy.fillFormSelect('permission', 'READ');
    });
    cy.screenshot('Doc_Report_Assign_Permission');
  });

  it('Tests Products In Use report', () => {
    cy.visit('/');
    cy.pressMainNavButton('Reports');
    cy.waitUntilContentLoaded();

    cy.screenshot('Doc_Report_Browser');

    cy.inMainNavContent(() => {
      cy.contains('tr', 'Products In Use').should('exist').click();
    });

    cy.screenshot('Doc_Report_Form');

    cy.inMainNavFlyin('app-report-form', () => {
      cy.get('button[data-cy=Generate]').click();
    });

    cy.inMainNavContent(() => {
      cy.contains('tr', instanceA).should('exist');
      cy.contains('tr', instanceB).should('exist');
      cy.contains('tr', instanceC).should('exist').click();
    });
    cy.screenshot('Doc_Report_Generated');
  });

  it('Tests Products In Use report with parameters', () => {
    cy.visit('/');
    cy.pressMainNavButton('Reports');
    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.contains('tr', 'Products In Use').should('exist').click();
    });

    cy.inMainNavFlyin('app-report-form', () => {
      cy.intercept({
        method: 'GET',
        url: '/api/report/productsInUse/parameter-options/products?instanceGroup=DemoGroupB',
      }).as('productParameterOptions');
      cy.fillFormSelect('instanceGroup', groupB);
      cy.wait('@productParameterOptions');
      cy.fillFormSelect('product', 'Demo Chat App');
      cy.fillFormInput('productVersion', '1.0.0');
      cy.fillFormSelect('purpose', 'TEST');

      cy.get('button[data-cy=Generate]').click();
    });

    cy.inMainNavContent(() => {
      cy.contains('tr', instanceB).should('exist');
      cy.contains('tr', instanceA).should('not.exist');
      cy.contains('tr', instanceC).should('not.exist');
    });
  });
});
