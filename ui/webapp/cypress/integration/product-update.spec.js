describe('Product Tests', () => {
  var instanceGroupName = 'Test-Group-' + new Date().getTime();
  var instanceUuid;

  beforeEach(() => {
    cy.login();
  });

  /**
   * Create the Instance Group
   */
  it('Creates an instance group', function () {
    cy.createInstanceGroup(instanceGroupName);
  });

  /**
   * Upload a Product
   */
  it('Uploads products', function () {
    cy.uploadProductIntoGroup(instanceGroupName, 'test-product-1-direct.zip');
    cy.uploadProductIntoGroup(instanceGroupName, 'test-product-2-direct.zip');
  });

  /**
   * Creates a new instance and sets the instanceUuid variable to the resulting UUID
   */
  it('Create a new instance', function () {
    cy.createInstance(instanceGroupName, 'ProductUpdateTest', 'STANDALONE', '1.0.0').then((uuid) => {
      instanceUuid = uuid;

      cy.get('body').contains(instanceUuid).should('exist');
    });
  });

  /**
   * Create a configuration for a server process
   * (copy from instance.spec.js)
   */
  it('Configure server process', function () {
    cy.visit('/#/instance/browser/' + instanceGroupName);
    cy.get('mat-card-subtitle').contains(instanceUuid).click();

    cy.get('[data-cy=instance-options]').clickContextMenuAction('Configure Applications');

    cy.getNodeCard('master')
      .contains('Drop server application here')
      .should('be.visible')
      .then((el) => {
        cy.contains('app-application-descriptor-card', 'Server Application').dragTo(el);
      });

    // wait for the application init to be done. a normal user will likely never see this :)
    cy.getApplicationConfigCard('master', 'Server Application').should('exist');
    cy.getApplicationConfigCard('master', 'Server Application').contains('Initializing...').should('not.exist');

    cy.contains('button', 'SAVE').click();
    cy.waitUntilContentLoaded();
  });

  it('Install & activate version 1.0.0', () => {
    cy.closeConfigureApplications();
    cy.getLatestInstanceVersion().installAndActivate();
    cy.getActiveInstanceVersion().contains('1.0.0').should('exist');
  });

  it('Upgrade to 2.0.0', () => {
    cy.get('.notifications-button').click();
    cy.contains('button', 'Show Product Versions').click();
    cy.contains('mat-toolbar', 'Change Product Version').should('exist');
    cy.contains('app-product-tag-card', '2.0.0').should('exist').contains('button', 'arrow_upward').click();

    cy.get('app-messagebox').contains('Product Upgrade').should('exist');
    cy.contains('button', 'OK').click();

    cy.getApplicationConfigCard('master', 'Server Application').find('.app-config-modified').should('exist');
    cy.get('app-instance-version-card').find('.instance-version-modified').should('exist');

    cy.contains('button', 'SAVE').click();
    cy.waitUntilContentLoaded();

    cy.get('app-instance-version-card').find('.instance-version-modified').should('not.exist');
    cy.contains('app-instance-version-card', '2.0.0').should('exist');
  });

  it('Synchronize configuration files', () => {
    cy.get('[data-cy=instance-options]').clickContextMenuAction('Configuration Files');
    cy.contains('button', 'Synchronize with Product').should('exist').click();

    cy.contains('tr', 'dummy3.cfg').then((row) => {
      cy.wrap(row).contains('button', 'edit').should('exist').and('be.enabled').click();
    });
    cy.contains('button', 'ACCEPT PRODUCT FILE').should('exist').click();

    cy.contains('tr', 'binary.cfg').then((row) => {
      cy.wrap(row).contains('button', 'edit').should('exist').and('be.enabled').click();
    });
    cy.contains('button', 'ACCEPT INSTANCE FILE').should('exist').click();

    cy.contains('button', 'Finish Synchronization').should('exist').click();
    cy.contains('button', 'SAVE').click();
  });

  it('Install & activate version 2.0.0 with new configuration', () => {
    cy.closeConfigureApplications();
    cy.getLatestInstanceVersion().installAndActivate();
    cy.getActiveInstanceVersion().contains('2.0.0').should('exist');
  });

  it('Configure new optional parameter', () => {
    cy.getApplicationConfigCard('master', 'Server Application').clickContextMenuAction('Configure');

    // set out parameter
    cy.addAndSetOptionalParameter('Test Parameters', 'Output', '{{P:DATA}}/cypress.txt');

    // set config file parameter
    cy.addAndSetOptionalParameter('Test Parameters', 'Config File', '{{P:CONFIG}}/cypress.cfg');

    cy.contains('button', 'APPLY').click();

    cy.getApplicationConfigCard('master', 'Server Application').find('.app-config-modified').should('exist');

    cy.contains('button', 'SAVE').click();
    cy.waitUntilContentLoaded();
    cy.get('app-instance-version-card').find('.instance-version-modified').should('not.exist');
  });

  it('Install & activate version 2.0.0', () => {
    cy.closeConfigureApplications();
    cy.getLatestInstanceVersion().installAndActivate();
    cy.getActiveInstanceVersion().contains('2.0.0').should('exist');
  });

  it('Downgrade to version 1.0.0', () => {
    cy.get('[data-cy=instance-options]').clickContextMenuAction('Change Product Version');
    cy.contains('mat-toolbar', 'Change Product Version').should('exist');

    cy.contains('app-product-tag-card', '2.0.0').should('exist').contains('button', 'check').and('be.visible');

    cy.contains('mat-slide-toggle', 'Show all').should('not.be.checked').click();
    cy.contains('app-product-tag-card', '1.0.0')
      .should('exist')
      .contains('button', 'arrow_downward')
      .should('be.enabled')
      .and('be.visible')
      .click();

    cy.get('app-messagebox').contains('Product Downgrade').should('exist');
    cy.contains('button', 'OK').click();

    cy.get('.notifications-button').click();
    cy.contains('button', 'Show Product Versions').should('be.visible').and('be.enabled');
    cy.get('.cdk-overlay-backdrop').click('top', { force: true, multiple: true });

    cy.getApplicationConfigCard('master', 'Server Application').find('.app-config-invalid').should('exist');
    cy.contains('button', 'SAVE').should('be.disabled');
  });

  it('Handle unknown parameters', () => {
    cy.getApplicationConfigCard('master', 'Server Application').clickContextMenuAction('Configure');

    cy.convertMissingToCustomParameter('param.out', 'Output');
    cy.deleteMissingParameter('Config File');

    cy.contains('button', 'APPLY').click();
    cy.getApplicationConfigCard('master', 'Server Application').find('.app-config-modified').should('exist');

    cy.contains('button', 'SAVE').click();
    cy.waitUntilContentLoaded();
    cy.get('app-instance-version-card').find('.instance-version-modified').should('not.exist');
  });

  /**
   * Delete the instance with the well-known UUID
   */
  it('Delete the instance', function () {
    cy.deleteInstance(instanceGroupName, instanceUuid);
    cy.deleteInstanceGroup(instanceGroupName);
  });
});
