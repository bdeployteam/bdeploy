describe('Instance Tests', function () {
  var instanceUuid;

  beforeEach(function () {
    cy.login();
  })

  /**
   * Creates a new instance and sets the instanceUuid variable to the resulting UUID
   */
  it('Create a new instance', function () {
    cy.createInstance('CreateInstanceTest').then(uuid => {
      instanceUuid = uuid;

      cy.get('body').contains(instanceUuid).should('exist');
    })
  })

  /**
   * Create a configuration for a server process
   */
  it('Configure server process', function () {
    cy.visit('/#/instance/browser/Test')
    cy.get('mat-card-subtitle').contains(instanceUuid).click();

    cy.get('app-instance-group-logo').parent().clickContextMenuItem('Configure Applications...');

    cy.getNodeCard('master').contains('Drop Application here').then(el => {
      cy.contains('app-application-descriptor-card', 'Server Application').dragTo(el);
    })

    // wait for the application init to be done. a normal user will likely never see this :)
    cy.getApplicationConfigCard('master', 'Server Application').should('exist')
    cy.getApplicationConfigCard('master', 'Server Application').contains('Initializing...').should('not.exist')

    cy.contains('button', 'SAVE').click();
  })

  /**
   * Sets the sleep parameter to a high value.
   * <p>
   * Also sets the text parameter to a recognizable value.
   */
  it('Set server parameters', function() {
    cy.getApplicationConfigCard('master', 'Server Application').clickContextMenuItem('Configure...')

    // set sleep parameter
    cy.addAndSetOptionalParameter('Sleep Configuration', 'Sleep Timeout', '120');

    // set text parameter
    cy.addAndSetOptionalParameter('Test Parameters', 'Text', 'CYPRESS');

    cy.contains('button', 'APPLY').click();

    cy.getApplicationConfigCard('master', 'Server Application').find('.app-config-modified').should('exist')

    cy.contains('button', 'SAVE').click();
  })

  /**
   * Install, activate the given instance configuration.
   */
  it('Install & activate', function() {
    cy.get('mat-loading-spinner').should('not.exist');

    // close the configura applications panel.
    cy.get('mat-toolbar').contains('Configure Applications').siblings('button').contains('close').click();

    // should be in the instance version list now, install
    cy.getLatestInstanceVersion().clickContextMenuItem('Install')

    // wait for progress and the icon to appear
    cy.getLatestInstanceVersion().find('mat-progress-spinner').should('not.exist')
    cy.getLatestInstanceVersion().contains('mat-icon', 'check_circle_outline').should('exist')

    // activate the installed instance version
    cy.getLatestInstanceVersion().clickContextMenuItem('Activate')

    // wait for progress and the icon to appear
    cy.getLatestInstanceVersion().find('mat-progress-spinner').should('not.exist')
    cy.getLatestInstanceVersion().contains('mat-icon', 'check_circle').should('exist')

    // unfortunately due to the nested 'find' call cannot assert 'not.exist' above
    cy.getActiveInstanceVersion().should('exist')

    // no error should have popped up.
    cy.get('snack-bar-container').should('not.exist')
  })

  /**
   * Start, check and stop the server process.
   */
  it('Start & stop server process', function() {
    // don't click 'somewhere' on the card, as there are buttons preventing events or doing something else.
    // pick some label to click...
    cy.getActiveInstanceVersion().contains('Version').click();
    cy.getApplicationConfigCard('master', 'Server Application').click();

    cy.contains('mat-toolbar', 'Process Control').should('exist');
    cy.contains('mat-toolbar', 'Server Application').should('exist');

    cy.contains('app-process-details', 'Server Application').within(() => {
      cy.contains('button', 'play_arrow').should('be.enabled').click();
      cy.get('app-process-status').find('.app-process-running').should('exist')
    })

    cy.getApplicationConfigCard('master', 'Server Application').within(() => {
      cy.get('app-process-status').find('.app-process-running').should('exist')
    })

    cy.contains('app-process-details', 'Server Application').within(() => {
      cy.contains('button', 'stop').click();
      cy.get('app-process-status').find('.app-process-stopped').should('exist')
    })
  })

  /**
   * Delete the instance with the well-known UUID
   */
  it('Delete the instance', function () {
    // make sure we're on the correct page :) this allows delete to work if previous tests failed.
    cy.visit('/#/instance/browser/Test')

    // open the menu on the card
    cy.contains('mat-card', instanceUuid).clickContextMenuItem('Delete')

    // place a trigger on the endpoint, so we can later wait for it
    cy.server()
    cy.route('GET', '/api/group/Test/instance').as('reload')

    // in the resulting dialog, click OK
    cy.get('mat-dialog-container').contains('button', 'OK').click();

    // wait for the dialog to disappear and the page to reload
    cy.wait('@reload')
    cy.get('mat-progress-spinner').should('not.exist')

    // now NO trace of the UUID should be left.
    cy.get('body').contains(instanceUuid).should('not.exist');
  })

  it('Cleanup instance group', function() {
    cy.visit('/#/manualcleanup');

    // calculate stuff
    cy.contains('button', 'Calculate Cleanup Actions').should('be.visible').and('be.enabled').click();

    // wait for the calculation to complete
    cy.get('mat-progress-spinner', { timeout: 10000 }).should('not.exist');

    // the execute button should be there
    cy.contains('button', 'Execute all Actions').as('Execute').should('exist').and('be.visible').and('be.enabled')

    // use td:contains to yield all cells which contain the text. .contains() only yields the first.
    // manifests on the slave are still there - meta (2 versions), node, app
    cy.get('td:contains(DELETE_MANIFEST)').should('have.length.greaterThan', 3);

    // stale application pool folder & instance data folder
    cy.get('td:contains(DELETE_FOLDER)').should('have.length.greaterThan', 1);

    // now clean up.
    cy.get('@Execute').scrollIntoView().click();

    // the calculate button should be back. re-calculate
    cy.contains('button', 'Calculate Cleanup Actions').should('exist').and('be.visible').and('be.enabled');
    cy.contains('button', 'Calculate Cleanup Actions').click();

    cy.get('mat-progress-spinner', { timeout: 10000 }).should('not.exist');
    cy.contains('button', 'Execute all Actions').should('exist').and('be.disabled');
    cy.contains('td', 'No actions').should('exist');

    cy.contains('button', 'Reset').click();
    cy.contains('button', 'Calculate Cleanup Actions').should('exist').and('be.visible').and('be.enabled');
  })
})
