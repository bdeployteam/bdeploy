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
  it('Create from template', function () {
    cy.visit('/#/instance/browser/Test')
    cy.waitUntilContentLoaded();

    cy.get('mat-card-subtitle').contains(instanceUuid).click();
    cy.waitUntilContentLoaded();

    cy.screenshot('BDeploy_Instance_Template_Empty');
    cy.contains('a', 'Instance Template').should('exist').and('be.visible').click();
    cy.contains('button', 'Default Configuration').click();

    cy.contains('div', 'Server Apps').contains('mat-select', 'None').click();
    cy.contains('mat-option', 'master').click();

    cy.contains('div', 'Client Apps').contains('mat-select', 'None').click();
    cy.contains('mat-option', 'Client Applications').click();

    cy.screenshot('BDeploy_Instance_Template_Dialog_Groups');

    cy.get('button[data-cy=next1]').click();

    cy.wait(100); // animation

    cy.get('[placeholder="Text Value"]').type('Test Value');
    cy.screenshot('BDeploy_Instance_Template_Dialog_Variables');

    cy.get('button[data-cy=next2]').click();
    cy.contains('button', 'Close').click();

    cy.getApplicationConfigCard('master', 'Server With Sleep').should('exist');
    cy.getApplicationConfigCard('master', 'Another Server With Sleep').should('exist');
    cy.getApplicationConfigCard('master', 'Server No Sleep').should('exist');

    cy.getApplicationConfigCard('Client Applications', 'Client Application').should('exist');

    cy.screenshot('BDeploy_Instance_Template_Processes');

    cy.contains('button', 'DISCARD').click();
    cy.get('mat-dialog-container').within(_ => {
      cy.contains('button', 'Yes').click();
    });

    cy.get('app-instance-group-logo').parent().find('button').contains('more_vert').click();
    cy.get('button').contains('Configure Applications').click();

    cy.contains('app-application-descriptor-card', 'Server Application').within(_ => {
      cy.contains('mat-select', 'Choose Application Template').click();
    });

    cy.screenshot('BDeploy_Application_Template_Choose');
    cy.contains('mat-option', 'Server With Sleep').click();

    cy.getNodeCard('master').contains('Drop server application here').should('be.visible').then(el => {
      cy.contains('app-application-descriptor-card', 'Server Application').dragTo(el);
    })

    cy.get('mat-dialog-container').within(_ => {
      cy.get('[placeholder="Sleep Timeout"]').clear().type('100');
      cy.screenshot('BDeploy_Application_Template_Variables', { padding: 20 });
      cy.contains('button', 'Apply').click();
    });

    cy.getApplicationConfigCard('master', 'Server With Sleep').should('exist');
    cy.screenshot('BDeploy_Application_Template_Process');

    cy.contains('button', 'SAVE').click();
  })

  /**
   * Delete the instance and the group
   */
  it('Delete the instance', function () {
    cy.deleteInstance('Test', instanceUuid);
    cy.deleteInstanceGroup('Test');
  })
})
