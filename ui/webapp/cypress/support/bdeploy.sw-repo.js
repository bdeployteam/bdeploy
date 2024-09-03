//@ts-check

/**
 * Command: createRepo
 */
Cypress.Commands.add('createRepo', function (repoName) {
  cy.visit('/');
  cy.pressMainNavButton('Software Repositories');

  cy.inMainNavContent(() => {
    cy.contains('tr', repoName).should('not.exist');
    cy.pressToolbarButton('Add Software Repository');
  });

  cy.inMainNavFlyin('app-add-repository', () => {
    cy.contains('button', 'Save').should('exist').and('be.disabled');

    cy.fillFormInput('name', repoName);
    cy.fillFormInput('description', repoName);

    cy.contains('button', 'Save').should('exist').and('be.enabled').click();
  });
  cy.checkMainNavFlyinClosed();

  cy.inMainNavContent(() => {
    cy.contains('tr', repoName).should('exist');
  });
});

/**
 * Command: enterRepo
 */
Cypress.Commands.add('enterRepo', function (repoName) {
  cy.visit('/');
  cy.pressMainNavButton('Software Repositories');

  cy.inMainNavContent(() => {
    cy.contains('tr', repoName).should('exist').click();
    cy.contains('mat-toolbar', repoName).should('exist');
  });
});

/**
 * Command: uploadProductIntoRepo
 */
Cypress.Commands.add('uploadProductIntoRepo', function (repoName, fileName) {
  cy.enterRepo(repoName);

  cy.get('app-repository').should('exist');

  cy.pressToolbarButton('Upload Software');

  cy.inMainNavFlyin('app-software-upload', () => {
    cy.fillFileDrop(fileName);
    cy.contains('app-bd-file-upload-raw', `Uploading: ${fileName}`).should('not.exist');
    cy.contains('app-bd-file-upload-raw', 'Success').should('exist');
  });
});
