describe('Instance Group Tests', () => {
  var groupName = 'Test-Group-' + new Date().getTime();

  beforeEach(() => {
    cy.login();
  });

  it('Create Instance Group', () => {
    cy.visit('/');

    cy.get('mat-progress-spinner').should('not.exist');
    cy.contains('button', 'add').click();

    cy.get('input[placeholder^="Instance group name"]').type(groupName);
    cy.get('input[placeholder=Description]').type('Automated Test Instance Group ' + groupName);

    cy.fixture('bdeploy.png').then(fileContent => {
      cy.get('input[type=file]').upload({ fileContent: fileContent, fileName: 'bdeploy.png', mimeType: 'image/png' });
    })

    cy.get('.logo-img').should('exist');

    cy.contains('button', 'SAVE').click();

    cy.get('[data-cy=group-' + groupName + ']').should('exist');
  });

  it('Delete Instance Group', () => {
    cy.visit('/');

    cy.get('[data-cy=group-' + groupName + ']')
      .as('group')
      .should('exist')
      .clickContextMenuItem('Delete');
    cy.contains('mat-dialog-container', 'Delete Instance Group: ' + groupName)
      .should('exist')
      .within(dialog => {
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
