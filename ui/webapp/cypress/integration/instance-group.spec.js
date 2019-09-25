describe('Instance Group Tests', () => {
  var groupName = 'Test-Group-' + new Date().getTime();

  beforeEach(() => {
    cy.login();
  });

  it('Create instance group', () => {
    cy.visit('/');

    cy.get('mat-progress-spinner').should('not.exist');
    cy.contains('button', 'add').click();

    cy.get('input[placeholder^="Instance group name"]').type(groupName);
    cy.get('input[placeholder=Description]').type('Automated Test Instance Group ' + groupName);

    cy.fixture('bdeploy.png').then(fileContent => {
      cy.get('input[type=file]').upload({ fileContent: fileContent, fileName: 'bdeploy.png', mimeType: 'image/png' });
    });

    cy.get('.logo-img').should('exist');

    cy.contains('button', 'SAVE').click();

    cy.get('[data-cy=group-' + groupName + ']').should('exist');
  });

  it('Upload product to instance group', function() {
    cy.get('[data-cy=group-' + groupName + ']')
      .first()
      .should('exist')
      .click();

    cy.get('button[mattooltip="Manage Products..."]')
      .should('be.visible')
      .and('be.enabled')
      .click();

    cy.contains('button', 'cloud_upload')
      .should('be.visible')
      .and('be.enabled')
      .click();

    cy.get('mat-dialog-container').within(() => {
      cy.fixture('test-product-1-direct.zip').then(zip => {
        cy.get('input[type=file]').upload({
          fileName: 'test-product-1-direct.zip',
          fileContent: zip,
          mimeType: 'application/zip',
        });
      });
      cy.fixture('test-product-2-direct.zip').then(zip => {
        cy.get('input[type=file]').upload({
          fileName: 'test-product-2-direct.zip',
          fileContent: zip,
          mimeType: 'application/zip',
        });
      });

      cy.contains('button', 'Upload')
        .should('be.enabled')
        .click();
      cy.get('td:contains("Upload successful")').should('have.length', 2);
      cy.contains('button', 'Close')
        .should('be.visible')
        .and('be.enabled')
        .click();
    });

    cy.contains('app-product-card', 'Demo Product')
      .should('exist')
      .click();

    cy.get('app-product-list')
      .contains('2.0.0')
      .should('exist');
    cy.get('app-product-list')
      .contains('1.0.0')
      .should('exist');

    cy.contains('app-product-list', '2.0.0').within(() => {
      cy.contains('button', 'info')
        .should('be.visible')
        .click();
    });

    cy.get('mat-card.info').within(() => {
      cy.contains('mat-chip', 'X-Product')
        .contains('io.bdeploy/demo')
        .should('exist');
    });
  });

  it('Create test instance', () => {
    cy.createInstance(groupName, 'InstanceUsingTestProduct').then(uuid => {
      cy.get('body').contains(uuid).should('exist');
    })
  })

  it('Delete instance group', () => {
    cy.visit('/');

    cy.get('[data-cy=group-' + groupName + ']')
      .as('group')
      .should('exist')
      .clickContextMenuItem('Delete');
    cy.contains('mat-dialog-container', 'Delete Instance Group: ' + groupName)
      .should('exist')
      .within(dialog => {
        cy.contains('Deleting this instance group will delete').should('exist');
        cy.contains('button', 'Delete').should('be.disabled');
        cy.get('input[placeholder="Instance Group Name"]').type('XX');
        cy.contains('button', 'Delete').should('be.disabled');
        cy.get('input[placeholder="Instance Group Name"]')
          .clear()
          .type(groupName);
        cy.contains('button', 'Delete')
          .should('be.enabled')
          .click();
      });

    cy.get('[data-cy=group-' + groupName + ']').should('not.exist');
  });
});
