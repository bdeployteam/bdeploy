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

    cy.fixture('bdeploy.png')
      .then(content => {
        return cy.get('input[type=file]').then(el => {
          return Cypress.Blob.base64StringToBlob(content, 'image/png').then(blob => {
            // use a DataTransfer to indirectly create a FileList.
            var f = new File([blob], 'logo.png', { type: 'image/png' });
            var dt = new DataTransfer();
            dt.items.add(f);

            // use the DataTransfer FileList to replace the one of the input, and trigger update.
            el[0].files = dt.files;
            el[0].dispatchEvent(new Event('change', { force: true }));
          });
        });
      });

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
