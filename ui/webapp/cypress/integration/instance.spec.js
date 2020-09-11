describe('Instance Tests', function () {
  var instanceUuid;

  beforeEach(function () {
    cy.login();
  })

  /**
   * Creates a new instance group and uploads a demo product
   */
  it('Create a new group', function () {
    cy.createInstanceGroup('Test');
    cy.uploadProductIntoGroup('Test', 'test-product-1-direct.zip');
    cy.uploadProductIntoGroup('Test', 'test-product-2-direct.zip');
  })

  /**
   * Creates a new instance within the previously created group.
   */
  it('Create a instance', function () {
    cy.createInstance('Test', 'CreateInstanceTest').then(uuid => {
      instanceUuid = uuid;

      cy.get('body').contains(instanceUuid).should('exist');
    })
  })

  /**
   * Create a configuration for a server process
   */
  it('Configure server process', function () {
    cy.visit('/#/instance/browser/Test')
    cy.waitUntilContentLoaded();

    cy.get('mat-card-subtitle').contains(instanceUuid).click();

    cy.get('app-instance-group-logo').parent().clickContextMenuAction('Configure Applications');

    cy.getNodeCard('master').contains('Drop server application here').should('be.visible').then(el => {
      cy.contains('app-application-descriptor-card', 'Server Application').dragTo(el);
    })

    // wait for the application init to be done. a normal user will likely never see this :)
    cy.getApplicationConfigCard('master', 'Server Application').should('exist')
    cy.getApplicationConfigCard('master', 'Server Application').contains('Initializing...').should('not.exist')

    cy.contains('button', 'SAVE').click();
    cy.waitUntilContentLoaded();
  })

  it('Create config file', () => {
    cy.visit('/#/instance/overview/Test/' + instanceUuid);
    cy.get('app-instance-group-logo').parent().clickContextMenuAction('Configuration Files');
    cy.contains('button', 'add').click();
    cy.get('input[data-placeholder="Enter path for file"]').clear().type('cypress.cfg')
    cy.typeInAceEditor('CY-CFG');
    cy.contains('button', 'APPLY').click();
    cy.contains('td', 'cypress.cfg').should('exist');
    cy.contains('button', 'SAVE').click();
    cy.waitUntilContentLoaded();
  })

  /**
   * Sets the sleep parameter to a high value.
   * <p>
   * Also sets the text parameter to a recognizable value.
   */
  it('Set server parameters', function() {
    cy.visit('/#/instance/overview/Test/' + instanceUuid);
    cy.getApplicationConfigCard('master', 'Server Application').clickContextMenuAction('Configure')

    // set sleep parameter
    cy.addAndSetOptionalParameter('Sleep Configuration', 'Sleep Timeout', '120');

    // set text parameter
    cy.addAndSetOptionalParameter('Test Parameters', 'Text', 'CYPRESS');

    // set out parameter
    cy.addAndSetOptionalParameter('Test Parameters', 'Output', '{{P:DATA}}/cypress.txt');

    // set config file parameter
    cy.addAndSetOptionalParameter('Test Parameters', 'Config File', '{{P:CONFIG}}/cypress.cfg');

    cy.contains('button', 'APPLY').click();

    cy.getApplicationConfigCard('master', 'Server Application').find('.app-config-modified').should('exist')

    cy.contains('button', 'SAVE').click();
    cy.waitUntilContentLoaded();
  })

  /**
   * Install, activate the given instance configuration.
   */
  it('Install & activate', function() {
    cy.visit('/#/instance/overview/Test/' + instanceUuid);
    cy.closeConfigureApplications();
    cy.getLatestInstanceVersion().installAndActivate();
  })

  /**
   * Start, check and stop the server process.
   */
  it('Start & stop server process', function() {
    cy.visit('/#/instance/overview/Test/' + instanceUuid);
    // don't click 'somewhere' on the card, as there are buttons preventing events or doing something else.
    // pick some label to click...
    cy.startProcess('master', 'Server Application');

    // click process output button
    cy.contains('app-process-details', 'Server Application').within(() => {
      cy.contains('button', 'message').click();
    })

    // check process output and close overlay
    cy.get('app-file-viewer').within(() => {
      cy.contains('button', 'close').click();
    })

    // check that process is marked as running
    cy.getApplicationConfigCard('master', 'Server Application').within(() => {
      cy.get('app-process-status').find('.app-process-running').should('exist')
    })

    // stop and check that the process is marked as stopped
    cy.contains('app-process-details', 'Server Application').within(() => {
      cy.contains('button', 'stop').click();
      cy.get('app-process-status').find('.app-process-stopped').should('exist')
    })
  })

  it('Check data file browser', () => {
    cy.visit('/#/instance/overview/Test/' + instanceUuid);
    cy.get('app-instance-group-logo').parent().clickContextMenuAction('Data Files');

    cy.contains('td', 'cypress.txt').click();

    // check file content and close overlay
    cy.get('app-file-viewer').within(() => {
      cy.contains('button', 'close').click();
    })

    cy.wait(200); // wait for the animation to complete and the backdrop to disappear.
    cy.contains('tr', 'cypress.txt').contains('mat-icon', 'cloud_download').should('be.visible').downloadFile('data.txt');
    cy.fixture('data.txt').then(txt => {
      expect(txt).to.contain('TEST');
    })
  })

  it('Export instance for future upgrade tests', () => {
    cy.visit('/#/instance/overview/Test/' + instanceUuid);
    cy.closeConfigureApplications();
    cy.getLatestInstanceVersion().find('button').contains('more_vert').click();
    cy.get('[role=menuitem]').contains('Export').should('be.enabled').downloadFile('export-test.zip');
  })

  it("Check the instance history",()=>{
    cy.visit('/#/instance/history/Test/' + instanceUuid);
    cy.waitUntilContentLoaded();

    cy.screenshot("BDeploy_User_History_Overview")

    // check if there is the right amount of events
    cy.get(".timeline_item").should("have.length","4");

    // open cards
    cy.contains("Version 4: Created").click();
    cy.contains("Version 3: Created").click();
    cy.screenshot("BDeploy_User_History_OpenedCard");

    // check if cards contain expected text
    cy.contains("mat-expansion-panel","Version 4: Created").should("contain.html","master")
      .and("contain.html","Parameter").and("contain.html","param.sleep");

    cy.contains("Version 4: Created").click();
    cy.contains("Version 3: Created").click();

    cy.contains("mat-expansion-panel","Version 2: Created").click()
      .should("contain.html","master:").and("contain.html","Server Application");

    // check comparison dialog
    cy.get(".history-compare-input").eq(0).type("1");
    cy.get(".history-compare-input").eq(1).type("4");
    cy.contains('button', 'compare_arrows').click();
    cy.get(".mat-dialog-container").should("contain.html","master:")
      .and("contain.html","Config files").and("contain.html","cypress.cfg");
    cy.screenshot("BDeploy_User_History_ComparisonDialog");

    // close compare dialog
    cy.get('.cdk-overlay-backdrop').click('top', {force:true, multiple: true});
    cy.contains("button","indeterminate_check_box").click();
    cy.wait(200); // wait until click animation of close-all button disappeared

    // show filter
    cy.contains("button","filter_list_alt").click();
    cy.screenshot("BDeploy_User_History_ShowMenu");

    // enable deployments and runtime
    cy.contains("button","Runtime").click();
    cy.contains("button","Deployment").click();
    cy.contains("button","filter_list_alt").click({force:true});
    cy.waitUntilContentLoaded();

    // and check them
    cy.contains("mat-expansion-panel","Server Application started").click()
      .should("contain.html","master").and("contain.html","4");
    cy.contains("mat-expansion-panel","Version 4: Activated").click()
      .should("contain.html","admin");
    cy.screenshot("BDeploy_User_History_RuntimeHistory");
  });

  /**
   * Delete the instance and the group
   */
  it('Delete the instance', function () {
    cy.deleteInstance('Test', instanceUuid);
    cy.deleteInstanceGroup('Test');
  })

  it('Cleanup instance group', function() {
    cy.visit('/#/admin/all/(panel:manualcleanup)');

    // calculate stuff
    cy.contains('button', 'Calculate Cleanup Actions').click();

    // wait for the calculation to complete
    cy.get('mat-spinner', { timeout: 10000 }).should('not.exist');

    // the execute button should be there
    cy.contains('button', 'Execute').as('Execute').should('exist').and('be.visible').and('be.enabled')

    // use td:contains to yield all cells which contain the text. .contains() only yields the first.
    // manifests on the slave are still there - meta (2 versions), node, app
    cy.get('td:contains(DELETE_MANIFEST)').should('have.length.greaterThan', 3);

    cy.get('.mat-paginator-navigation-last').should('exist').and('be.enabled');
    cy.get('.mat-paginator-navigation-last').click();

    // stale application pool folder & instance data folder
    cy.get('td:contains(DELETE_FOLDER)').should('have.length.greaterThan', 1);

    // now clean up.
    cy.get('@Execute').scrollIntoView().click();

    // the calculate button should be back. re-calculate
    cy.contains('button', 'Execute').should('not.exist'); // avoid finding the 'Re-Calculate ...' button.
    cy.contains('button', 'Calculate Cleanup Actions').click();

    cy.get('mat-spinner', { timeout: 10000 }).should('not.exist');
    cy.contains('button', 'Execute all Actions').should('exist').and('be.disabled');
    cy.contains('p', 'No actions').should('exist');

    cy.contains('button', 'Reset').click();
    cy.contains('button', 'Calculate Cleanup Actions').should('exist').and('be.visible').and('be.enabled');
  })
})
