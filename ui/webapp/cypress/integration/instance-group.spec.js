describe('Instance Group Tests', () => {
  var groupName = 'Test-Group-' + new Date().getTime();

  beforeEach(() => {
    cy.login();
  });

  it('Create instance group', () => {
    cy.createInstanceGroup(groupName);
  });

  it('Upload product to instance group', function() {
    cy.uploadProductIntoGroup(groupName, 'test-product-1-direct.zip');
    cy.uploadProductIntoGroup(groupName, 'test-product-2-direct.zip');
    cy.verifyProductVersion(groupName, "Demo Product", "io.bdeploy/demo", "1.0.0");
    cy.verifyProductVersion(groupName, "Demo Product", "io.bdeploy/demo", "2.0.0");
  });

  it('Create test instance', () => {
    cy.createInstance(groupName, 'InstanceUsingTestProduct').then(uuid => {
      cy.get('body').contains(uuid).should('exist');
    })
  })

  it('Delete instance group', () => {
    cy.visit('/');
    cy.deleteInstanceGroup(groupName);
  });
});
