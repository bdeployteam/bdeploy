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

  it('Upload product to instance group', function () {
    cy.uploadProductIntoGroup(groupName, 'test-product-1-direct.zip');
    cy.uploadProductIntoGroup(groupName, 'test-product-2-direct.zip');
    cy.verifyProductVersion(groupName, 'Demo Product', '1.0.0');
    cy.verifyProductVersion(groupName, 'Demo Product', '2.0.0');
  });

  it('Creates an instance', () => {
    cy.createInstance(groupName, instanceName, 'Demo Product', '1.0.0');
  });

  // TODO delete instance

  // TODO group permission tests

  it('Deletes the group', () => {
    cy.visit('/');
    cy.deleteGroup(groupName);
  });
});
