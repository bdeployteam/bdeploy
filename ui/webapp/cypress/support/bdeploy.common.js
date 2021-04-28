Cypress.Commands.add('waitUntilContentLoaded', function () {
  cy.document()
    .its('body')
    .within(() => {
      cy.get('ngx-loading-bar').children().should('not.exist');
      cy.get('span:contains("Loading Module...")').should('not.exist');
      cy.get('app-bd-loading-overlay[data-cy="loading"]').should('not.exist');
    });
});

Cypress.Commands.add('pressMainNavButton', function (text) {
  cy.get('app-main-nav-menu').within(() => {
    cy.get(`app-main-nav-button[text="${text}"]`).click();
  });
});

Cypress.Commands.add('inMainNavContent', function (callback) {
  cy.get('app-main-nav-content').should('exist').within(callback);
});

Cypress.Commands.add('inMainNavFlyin', function (title, callback) {
  cy.get(`app-bd-dialog-toolbar[header="${title}"]`).should('exist');
  cy.get('app-main-nav-flyin[data-cy="open"]').should('exist').within(callback);
});

Cypress.Commands.add('checkMainNavFlyinClosed', function () {
  cy.get('app-main-nav-flyin[data-cy="closed"]').should('exist');
});

Cypress.Commands.add('pressToolbarPanelButton', function (text) {
  cy.get('app-bd-dialog-toolbar').within(() => {
    cy.get(`app-bd-panel-button[text="${text}"]`).click();
  });
});

Cypress.Commands.add('pressToolbarButton', function (text) {
  cy.get('app-bd-dialog-toolbar').within(() => {
    cy.get(`app-bd-button[text="${text}"]`).click();
  });
});

Cypress.Commands.add('fillFormInput', function (name, data) {
  const s = 'app-bd-form-input' + (name === undefined ? '' : `[name="${name}"]`);
  cy.get(s).within(() => {
    cy.get('mat-form-field').should('exist').click();
    cy.get('input').should('exist').and('have.focus').type(data);
  });
});

Cypress.Commands.add('fillFormSelect', function (name, data) {
  const s = 'app-bd-form-select' + (name === undefined ? '' : `[name="${name}"]`);
  cy.get(s).within(() => {
    cy.get('mat-form-field').should('exist').click();
    // escape all .within scopes to find the global overlay content
    cy.document().its('body').find('.cdk-overlay-container').contains('mat-option', data).should('exist').click();
  });
});

Cypress.Commands.add('fillImageUpload', function (filePath, mimeType, checkEmpty = true) {
  cy.get('app-bd-image-upload').within(() => {
    if (checkEmpty) {
      cy.get('img[src="/assets/no-image.svg"]').should('exist');
    }
    cy.get('input[type="file"]').attachFile({ filePath: filePath, mimeType: mimeType });
    cy.get('img[src="/assets/no-image.svg"]').should('not.exist');
    cy.get('img[alt="logo"]').should('exist');
  });
});

Cypress.Commands.add('fillFileDrop', function (name, mimeType) {
  cy.get('app-bd-file-drop').within(() => {
    cy.get('input[type="file"]').attachFile({ filePath: name, mimeType: mimeType });
  });
});

Cypress.Commands.add('checkFileUpload', function (name, mimeType) {
  cy.get('app-bd-file-upload').within(() => {
    cy.contains('div', `Success: ${name}`);
  });
});
