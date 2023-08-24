//@ts-check

Cypress.Commands.add('waitUntilContentLoaded', function () {
  cy.document()
    .its('body')
    .within(() => {
      cy.get('ngx-loading-bar').children().should('not.exist');
      cy.get('span:contains("Loading Module...")').should('not.exist');
      cy.get('app-bd-loading-overlay[data-cy="loading"]').should('not.exist');
      cy.document().its('body').find('button mat-spinner').should('not.exist'); // no work-in-progress buttons
    });
});

Cypress.Commands.add('waitForApi', function (callback) {
  cy.intercept({ url: '/api/**' }).as('apiCall');
  callback();
  // a little tricky; REST calls can trigger a WebSocket message back to the client which in turn can
  // then trigger another REST call (e.g. reloading).
  cy.wait('@apiCall');

  // might trigger an update of the UI, want to wait for that as well.
  cy.waitUntilContentLoaded();
});

Cypress.Commands.add('pressMainNavTopButton', function (text) {
  cy.get('app-main-nav-top').within(() => {
    cy.get(`app-bd-panel-button[text="${text}"]`).click();
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

Cypress.Commands.add('inMainNavFlyin', function (flyinComponent, callback) {
  cy.get(flyinComponent).should('exist');
  cy.get('app-main-nav-flyin[data-cy="open"]').should('exist').within(callback);
});

Cypress.Commands.add('checkMainNavFlyinClosed', function () {
  cy.get('app-main-nav-flyin[data-cy="closed"]').should('exist');
});

Cypress.Commands.add('pressToolbarButton', function (text) {
  cy.get('app-bd-dialog-toolbar').within(() => {
    cy.get(`button[data-cy^="${text}"]`).click();
  });
});

Cypress.Commands.add('fillFormInput', function (name, data) {
  const s =
    'app-bd-form-input' + (name === undefined ? '' : `[name="${name}"]`);
  cy.get(s).within(() => {
    cy.get('mat-form-field').should('exist').click();
    cy.get('input')
      .not('[type=checkbox]') // skip "pin" checkbox in process parameter fields.
      .should('exist')
      .and('have.focus')
      .and('be.enabled')
      .clear()
      .type(data, { parseSpecialCharSequences: false }); // required for variable literals.
  });
});

Cypress.Commands.add('fillFormSelect', function (name, data) {
  const s =
    'app-bd-form-select' + (name === undefined ? '' : `[name="${name}"]`);
  cy.get(s).within(() => {
    cy.get('mat-form-field').should('exist').click();
    // escape all .within scopes to find the global overlay content
    cy.document()
      .its('body')
      .find('.cdk-overlay-container')
      .contains('mat-option', data)
      .should('exist')
      .click();
  });
});

Cypress.Commands.add('fillFormToggle', function (name) {
  const s =
    'app-bd-form-toggle' + (name === undefined ? '' : `[name="${name}"]`);
  cy.get(s).should('exist').click();
});

Cypress.Commands.add(
  'fillImageUpload',
  function (filePath, mimeType, checkEmpty = true) {
    cy.get('app-bd-image-upload').within(() => {
      if (checkEmpty) {
        cy.get('img[src="/assets/no-image.svg"]').should('exist');
      }
      cy.get('input[type="file"]').selectFile(
        {
          contents: Cypress.config('fixturesFolder') + '/' + filePath,
          mimeType: mimeType,
        },
        { force: true }
      );
      cy.get('img[src="/assets/no-image.svg"]').should('not.exist');
      cy.get('img[alt="logo"]').should('exist');
    });
  }
);

Cypress.Commands.add('fillFileDrop', function (name, mimeType = undefined) {
  cy.get('app-bd-file-drop').within(() => {
    cy.get('input[type="file"]').selectFile(
      {
        contents: Cypress.config('fixturesFolder') + '/' + name,
        mimeType: mimeType,
      },
      { force: true }
    );
  });
});

Cypress.Commands.add('checkFileUpload', function (name) {
  cy.get('app-bd-file-upload').within(() => {
    cy.contains('div', `Success: ${name}`);
  });
});

Cypress.Commands.add('checkAndConfirmSnackbar', function (message) {
  cy.document()
    .its('body')
    .find('mat-snack-bar-container')
    .within(() => {
      cy.contains('simple-snack-bar', message).should('exist');
      cy.contains('button', 'DISMISS').should('exist').click();
      cy.contains('simple-snack-bar', message).should('not.exist');
    });
});

// INTERNAL
Cypress.Commands.add('downloadFromLinkHref', function (link) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('GET', link.href);
    xhr.onload = () => {
      resolve(xhr);
    };
    xhr.onerror = () => {
      reject(xhr);
    };
    xhr.send();
  });
});

Cypress.Commands.add(
  'downloadByLinkClick',
  { prevSubject: true },
  function (subject, filename, fixture = false) {
    cy.window().then((win) => {
      let url = null;
      const stubbed = cy
        // @ts-ignore
        .stub(win.downloadLocation, 'click')
        .onFirstCall()
        .callsFake((link) => {
          url = link;
        });

      cy.wrap(subject)
        .click()
        .should(() => {
          expect(stubbed).to.be.calledOnce;
          expect(url).to.be.not.null;
        })
        .then(() => {
          stubbed.restore();

          // @ts-ignore
          cy.downloadFromLinkHref(url).then((rq) => {
            expect(rq.status).to.equal(200);
            cy.writeFile(
              Cypress.config('downloadsFolder') + '/' + filename,
              rq.response
            ).then(() => {
              if (fixture) {
                cy.task('moveFile', {
                  from: Cypress.config('downloadsFolder') + '/' + filename,
                  to: Cypress.config('fixturesFolder') + '/' + filename,
                });
              }
            });
          });
        });
    });
  }
);

Cypress.Commands.add(
  'downloadByLocationAssign',
  { prevSubject: true },
  (subject, filename, fixture = false) => {
    cy.window().then((win) => {
      let url = null;
      const stubbed = cy
        // @ts-ignore
        .stub(win.downloadLocation, 'assign')
        .onFirstCall()
        .callsFake((link) => {
          url = link;
        });

      cy.wrap(subject)
        .click()
        .should(() => {
          expect(stubbed).to.be.calledOnce;
          expect(url).to.be.not.null;
        })
        .then(() => {
          stubbed.restore();

          if (url.startsWith('/')) {
            // URL argument is relative in production, see application config.json
            url = Cypress.config('baseUrl') + url;
          }

          cy.task('downloadFileFromUrl', {
            url: url,
            fileName: Cypress.config('downloadsFolder') + '/' + filename,
          }).then(() => {
            if (fixture) {
              cy.task('moveFile', {
                from: Cypress.config('downloadsFolder') + '/' + filename,
                to: Cypress.config('fixturesFolder') + '/' + filename,
              });
            }
          });
        });
    });
  }
);

Cypress.Commands.add('typeInMonacoEditor', function (text, clear = false) {
  if (clear) {
    const selectAllKeys = Cypress.platform == 'darwin' ? '{cmd}a' : '{ctrl}a';
    cy.monacoEditor()
      .click({ force: true })
      .focused()
      .type(selectAllKeys)
      .clear();
  }
  cy.monacoEditor().click({ force: true }).focused().type(text);
});

Cypress.Commands.add('monacoEditor', function () {
  cy.get('.bd-editor-initializing').should('not.exist');
  return cy.get('.monaco-editor textarea:first').should('exist');
});
