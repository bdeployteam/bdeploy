describe('Groups Tests', () => {
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

  // TODO instance tests

  it('cleans up', () => {
    cy.deleteInstance(groupName, instanceName);
    cy.deleteGroup(groupName);
  });
});
