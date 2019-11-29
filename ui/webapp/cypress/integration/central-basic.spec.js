describe('Central/Managed Basic Test', function() {
  // don't use a dynamic name. this variable is re-initialized on /every/ cy.visit().
  var groupName = 'CentralGroup-' + new Date().getTime();

  it('Visit Central Server', function() {
    cy.visitBDeploy('/', 'CENTRAL');
    cy.contains('mat-toolbar', 'CENTRAL').within(() => {
      cy.contains('button', 'menu').click();
    })
    cy.contains('mat-sidenav', 'CENTRAL').should('exist').and('be.visible');
  })

  it('Visit Managed Server', function() {
    cy.visitBDeploy('/', 'MANAGED');
    cy.contains('mat-toolbar', 'MANAGED').within(() => {
      cy.contains('button', 'menu').click();
    })
    cy.contains('mat-sidenav', 'MANAGED').should('exist').and('be.visible');
  })

  it('creates instance group on central', () => {
    cy.createInstanceGroup(groupName, 'CENTRAL');
  })

  it('attach managed server to central', () => {
    cy.visitBDeploy('/', 'MANAGED');

    cy.contains('button', 'link').should('exist').and('be.enabled').click();

    cy.contains('button', 'Next').should('exist').and('be.enabled').click();
    cy.contains('button', 'Continue Manually').should('exist').and('be.enabled').click();

    cy.contains('button', 'Download').should('exist').and('be.enabled').downloadBlobFile('managed-ident.json');

    cy.visitBDeploy('/', 'CENTRAL');
    cy.get('[data-cy=group-' + groupName + ']')
      .should('exist')
      .clickContextMenuItem('Managed Servers...');

    cy.waitUntilContentLoaded();

    cy.contains('button', 'add').should('exist').and('be.enabled').click();
    cy.contains('button', 'Next').should('exist').and('be.enabled').click();

    cy.contains('mat-step-header', 'Attach Managed Server').parent().within(e => {
      cy.fixture('managed-ident.json').then(json => {
        cy.get('input[data-cy="managed-ident"]').upload({
          fileName: 'managed-ident.json',
          fileContent: JSON.stringify(json),
          mimeType: 'application/json',
        });
      });

      cy.contains('Successfully read information for').should('exist').and('be.visible');
      cy.contains('button', 'Next').should('exist').and('be.visible').and('be.enabled').click();
    })

    cy.contains('mat-step-header', 'Additional Information').parent().within(e => {
      cy.get('input[placeholder=Description]').should('exist').and('be.visible').and('be.empty').type('Test Local Server');
      cy.contains('button', 'Next').should('exist').and('be.visible').and('be.enabled').click();
    });

    // magic happens here :)

    cy.contains('mat-step-header', 'Done').parent().within(e => {
      cy.contains('button', 'Done').should('exist').and('be.visible').and('be.enabled').click();
    });

    // we're on the managed servers page again now. verify server exists and can be sync'd.
    cy.contains('mat-expansion-panel', 'Test Local Server').should('exist').and('be.visible').within(e => {
      cy.contains('button', 'Synchronize').should('exist').and('be.enabled').click();

      // don't use waitUntilContentLoaded as it does not work in within blocks.
      cy.get('mat-spinner').should('not.exist');

      cy.contains('span', 'Last sync').should('contain.text', new Date().getFullYear());
      cy.contains('td', 'favorite').should('exist'); // the green heart.
    });
  })

  it('deletes instance group', () => {
    cy.deleteInstanceGroup(groupName, 'CENTRAL');
  })
})
