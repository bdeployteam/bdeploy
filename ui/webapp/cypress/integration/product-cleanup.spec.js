describe('Product Cleanup Test', function() {
  var instanceGroupName = 'Test-Group-' + new Date().getTime();
  var instanceName = 'Test-Instance-' + new Date().getTime();

  var instanceUuid;

  this.beforeEach(function() {
    cy.login();
  })

  /**
   * Create the Instance Group
   */
  it('Creates an instance group', function() {
    cy.createInstanceGroup(instanceGroupName);
  })

  /**
   * Upload a Product
   */
  it('Uploads products', function() {
    cy.uploadProductIntoGroup(instanceGroupName, 'test-product-1-direct.zip');
    cy.uploadProductIntoGroup(instanceGroupName, 'test-product-2-direct.zip');
  })

  /**
   * Create the Instance
   */
  it('Creates an instance', function() {
    cy.createInstance(instanceGroupName, instanceName, '1.0.0').then(uuid => {
      instanceUuid = uuid;

      cy.get('body').contains(instanceUuid).should('exist');
    });
  })

 /**
  * Configure a server application
  */
  it('Configures a server application', function() {
    cy.gotoInstance(instanceGroupName, instanceUuid);

    cy.get('app-instance-group-logo').parent().clickContextMenuItem('Configure Applications...');

    cy.getNodeCard('master').contains('Drop server application here').then(el => {
      cy.contains('app-application-descriptor-card', 'Server Application').dragTo(el);
    })

    // wait for the application init to be done. a normal user will likely never see this :)
    cy.getApplicationConfigCard('master', 'Server Application').should('exist')
    cy.getApplicationConfigCard('master', 'Server Application').contains('Initializing...').should('not.exist')

    cy.contains('button', 'SAVE').click();
  })

  /**
   * Install and activate two instance versions (v2)
   */
  it('Installs and activates the latest instance version (v2)', function() {
    cy.gotoInstance(instanceGroupName, instanceUuid);
    cy.getLatestInstanceVersion().installAndActivate();
  })

  /**
   * Create a new instance version (v3)
   */
  it('Creates a new instance version (v3)', function() {
    cy.createNewInstanceVersionByDummyChange(instanceGroupName, instanceUuid, 'master', 'Server Application');
  })

  /**
   * Install and activate latest instance version (v3)
   */
  it('Installs and activates the latest instance version (v3)', function() {
    cy.gotoInstance(instanceGroupName, instanceUuid);
    cy.getLatestInstanceVersion().installAndActivate();
  })

  /**
   * calculate Manual Cleanup -> assert: no instance versions or products to uninstall
   */
  it('Calculates a manual cleanup with empty result', function() {
    cy.visit('/#/manualcleanup');
    // calculate stuff
    cy.contains('button', 'Calculate Cleanup Actions').should('be.visible').and('be.enabled').click();
    // wait for the calculation to complete
    cy.get('mat-spinner', { timeout: 10000 }).should('not.exist');
    // the execute button should be there
    cy.contains('button', 'Execute').as('Execute').should('exist').and('be.visible').and('be.disabled')
    // checks...
    cy.contains('td', 'UNINSTALL_INSTANCE_VERSION').should('not.exist');
    cy.contains('td', 'Delete product').should('not.exist');
  })

  /**
   * Create a new instance version (v4)
   */
  it('Creates a new instance version (v4)', function() {
    cy.createNewInstanceVersionByDummyChange(instanceGroupName, instanceUuid, 'master', 'Server Application');
  })

  /**
   * Install and activate latest instance version (v4)
   */
  it('Installs and activates the latest instance version (v4)', function() {
    cy.gotoInstance(instanceGroupName, instanceUuid);
    cy.getLatestInstanceVersion().installAndActivate();
  })

  /**
   * calculate Manual Cleanup -> assert: uninstall (v2)
   */
  it('Calculates a manual cleanup for uninstalling instance version v2', function() {
    cy.visit('/#/manualcleanup');
    // calculate stuff
    cy.contains('button', 'Calculate Cleanup Actions').should('be.visible').and('be.enabled').click();
    // wait for the calculation to complete
    cy.get('mat-spinner', { timeout: 10000 }).should('not.exist');
    // the execute button should be there
    cy.contains('button', 'Execute').as('Execute').should('exist').and('be.visible').and('be.enabled')
    // checks...
    cy.contains('td', 'Uninstall instance version "' + instanceName + '", version "2"').should('exist');
    cy.get('td:contains("UNINSTALL_INSTANCE_VERSION")').should('have.length', 1);
    cy.contains('td', 'Delete product').should('not.exist');
  })

  /**
   * Product upgrade creates a new instance version (v5)
   */
  it('Upgrades the product to 2.0.0', function() {
    cy.gotoInstance(instanceGroupName, instanceUuid);
    cy.contains('button', 'Newer product version available').should('be.visible').and('be.enabled').click();
    cy.contains('mat-toolbar', 'Change Product Version').should('exist');
    cy.contains('app-product-tag-card', '2.0.0').should('exist').contains('button', 'arrow_upward').should('be.enabled').and('be.visible').click();

    cy.getApplicationConfigCard('master', 'Server Application').find('.app-config-modified').should('exist');
    cy.get('app-instance-version-card').find('.instance-version-modified').should('exist')

    cy.contains('button', 'SAVE').click();

    cy.get('app-instance-version-card').find('.instance-version-modified').should('not.exist')
    cy.contains('app-instance-version-card', '2.0.0').should('exist');
  })

  /**
   * Install and activate latest instance version (v5)
   */
  it('Installs and activates the latest instance version (v5)', function() {
    cy.gotoInstance(instanceGroupName, instanceUuid);
    cy.getLatestInstanceVersion().installAndActivate();
  })

  /**
   * calculate Manual Cleanup -> assert: uninstall (v2) + (v3)
   */
  it('Calculates a manual cleanup for uninstalling instance version v2 + v3', function() {
    cy.visit('/#/manualcleanup');
    // calculate stuff
    cy.contains('button', 'Calculate Cleanup Actions').should('be.visible').and('be.enabled').click();
    // wait for the calculation to complete
    cy.get('mat-spinner', { timeout: 10000 }).should('not.exist');
    // the execute button should be there
    cy.contains('button', 'Execute').as('Execute').should('exist').and('be.visible').and('be.enabled')

    // checks...
    cy.get('td:contains("UNINSTALL_INSTANCE_VERSION")').should('have.length', 2);
    cy.contains('td', 'Uninstall instance version "' + instanceName + '", version "2"').should('exist');
    cy.contains('td', 'Uninstall instance version "' + instanceName + '", version "3"').should('exist');
    cy.contains('td', 'Delete product').should('not.exist');
  })

  /**
   * Create a new instance version (v6)
   */
  it('Creates a new instance version (v6)', function() {
    cy.createNewInstanceVersionByDummyChange(instanceGroupName, instanceUuid, 'master', 'Server Application');
  })

  /**
   * Install and activate latest instance version (v6)
   */
  it('Installs and activates the latest instance version (v6)', function() {
    cy.gotoInstance(instanceGroupName, instanceUuid);
    cy.getLatestInstanceVersion().installAndActivate();
  })

  /**
   * calculate Manual Cleanup -> assert: uninstall (v2)...(v4), delete product version 1.0.0
   */
  it('Calculates a manual cleanup for uninstalling instance version v2..v4 and product version 1.0.0', function() {
    cy.visit('/#/manualcleanup');
    // calculate stuff
    cy.contains('button', 'Calculate Cleanup Actions').should('be.visible').and('be.enabled').click();
    // wait for the calculation to complete
    cy.get('mat-spinner', { timeout: 10000 }).should('not.exist');
    // the execute button should be there
    cy.contains('button', 'Execute').as('Execute').should('exist').and('be.visible').and('be.enabled')

    // checks...
    cy.get('td:contains("UNINSTALL_INSTANCE_VERSION")').should('have.length', 3);
    cy.contains('td', 'Uninstall instance version "' + instanceName + '", version "2"').should('exist');
    cy.contains('td', 'Uninstall instance version "' + instanceName + '", version "3"').should('exist');
    cy.contains('td', 'Uninstall instance version "' + instanceName + '", version "4"').should('exist');
    cy.get('td:contains("DELETE_MANIFEST")').should('have.length.gte', 5);
    cy.contains('td', 'Delete product "Demo Product", version "1.0.0"').should('exist');
    cy.get('td:contains("Delete Application")').should('have.length.gte', 4);
  })

  /**
   * perform cleanup & check result
   */
  it('Cleanup instance group', function() {
    // now clean up (continues previous result).
    cy.contains('button', 'Execute').scrollIntoView().click();

    // the calculate button should be back. re-calculate
    cy.contains('button', 'Execute').should('not.exist'); // avoid finding the 'Re-Calculate ...' button.
    cy.contains('button', 'Calculate Cleanup Actions').should('exist').and('be.visible').and('be.enabled').click();

    cy.get('mat-spinner', { timeout: 10000 }).should('not.exist');
    cy.contains('td', 'Uninstall instance version').should('not.exist');
    cy.contains('td', 'Delete product').should('not.exist');
  })


  /**
   * Delete the Instance Group
   */
  it('Deletes the instance group', function() {
    cy.deleteInstanceGroup(instanceGroupName);
  })

})
