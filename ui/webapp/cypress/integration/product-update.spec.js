describe('Product Tests', () => {
  var instanceUuid;

  beforeEach(() => {
    cy.login();
  });

  /**
   * Creates a new instance and sets the instanceUuid variable to the resulting UUID
   */
  it('Create a new instance', function () {
    cy.createInstance('Test', 'ProductUpdateTest', '1.0.0').then(uuid => {
      instanceUuid = uuid;

      cy.get('body').contains(instanceUuid).should('exist');
    })
  })

  /**
   * Create a configuration for a server process
   * (copy from instance.spec.js)
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

  it('Install & activate version 1.0.0', () => {
    cy.getLatestInstanceVersion().installAndActivate();
    cy.getActiveInstanceVersion().contains('1.0.0').should('exist');
  })

  it('Upgrade to 2.0.0', () => {
    cy.contains('button', 'Newer product version available').should('be.visible').and('be.enabled').click();
    cy.contains('mat-toolbar', 'Change Product Version').should('exist');
    cy.contains('app-product-tag-card', '2.0.0').should('exist').contains('button', 'arrow_upward').should('be.enabled').and('be.visible').click();

    cy.getApplicationConfigCard('master', 'Server Application').find('.app-config-modified').should('exist');
    cy.get('app-instance-version-card').find('.instance-version-modified').should('exist')

    cy.contains('button', 'SAVE').click();

    cy.get('app-instance-version-card').find('.instance-version-modified').should('not.exist')
    cy.contains('app-instance-version-card', '2.0.0').should('exist');
  })

  it('Install & activate version 2.0.0', () => {
    cy.getLatestInstanceVersion().installAndActivate();
    cy.getActiveInstanceVersion().contains('2.0.0').should('exist');
  })

  /**
   * Delete the instance with the well-known UUID
   */
  it('Delete the instance', function () {
    cy.deleteInstance('Test', instanceUuid)
  })
})
