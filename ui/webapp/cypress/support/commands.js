// ***********************************************
// This example commands.js shows you how to
// create various custom commands and overwrite
// existing commands.
//
// For more comprehensive examples of custom
// commands please read more here:
// https://on.cypress.io/custom-commands
// ***********************************************
//
//
// -- This is a parent command --
// Cypress.Commands.add("login", (email, password) => { ... })
//
//
// -- This is a child command --
// Cypress.Commands.add("drag", { prevSubject: 'element'}, (subject, options) => { ... })
//
//
// -- This is a dual command --
// Cypress.Commands.add("dismiss", { prevSubject: 'optional'}, (subject, options) => { ... })
//
//
// -- This is will overwrite an existing command --
// Cypress.Commands.overwrite("visit", (originalFn, url, options) => { ... })

import './bdeploy.login';
import './bdeploy.instance';
import './bdeploy.instance-group';
import './bdeploy.node';
import './bdeploy.admin';
import './dragdrop';
import 'cypress-file-upload';

Cypress.Commands.add('clickContextMenuDialog', { prevSubject: true }, function (subject, item, dialogItem) {
  let wrapped = cy.wrap(subject);
  wrapped.find('button').contains('more_vert').click();
  cy.get('[role=menuitem]').contains(item).should('be.enabled').click().should('not.exist');
  if (dialogItem) {
    cy.contains('mat-dialog-container', dialogItem).should('exist');
  } else {
    cy.get('div.cdk-overlay-backdrop').should('exist');
  }
  return wrapped;
});

Cypress.Commands.add('clickContextMenuAction', { prevSubject: true }, function (subject, item) {
  let wrapped = cy.wrap(subject);
  wrapped.contains('more_vert').click();
  cy.get('[role=menuitem]').contains(item).should('be.enabled').click().should('not.exist');
  cy.get('div.cdk-overlay-backdrop').should('not.exist');
  return wrapped;
});

Cypress.Commands.add('downloadObjectUrl', function (link) {
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

Cypress.Commands.add('downloadBlobFileShould', { prevSubject: true }, function (subject, callback) {
  cy.window().then((win) => {
    const stubbed = cy.stub(win.downloadLocation, 'click', (link) => {});

    cy.wrap(subject)
      .click()
      .should(() => {
        expect(stubbed).to.be.calledOnce;

        const link = stubbed.args[0][0];
        cy.downloadObjectUrl(link).then((rq) => {
          expect(rq.status).to.equal(200);
          callback(rq.response);
        });
      });
  });
});

Cypress.Commands.add('downloadBlobFile', { prevSubject: true }, function (subject, filename) {
  cy.window().then((win) => {
    const stubbed = cy.stub(win.downloadLocation, 'click', (link) => {});

    cy.wrap(subject)
      .click()
      .should(() => {
        expect(stubbed).to.be.calledOnce;

        const link = stubbed.args[0][0];
        cy.downloadObjectUrl(link).then((rq) => {
          expect(rq.status).to.equal(200);

          cy.writeFile('cypress/fixtures/' + filename, rq.response);
        });
      });
  });
});

Cypress.Commands.add('downloadFile', { prevSubject: true }, (subject, fileName) => {
  cy.window().then((win) => {
    const stubbed = cy.stub(win.downloadLocation, 'assign', (link) => {});

    cy.wrap(subject)
      .click()
      .should(() => {
        expect(stubbed).to.be.calledOnce;

        var url = stubbed.args[0][0];

        if (url.startsWith('/api')) {
          url = url.substring(4);
        }
        if (url.startsWith('/')) {
          // URL argument is relative in production, see application config.json
          url = Cypress.env('backendBaseUrl') + url;
        }

        cy.task('downloadFileFromUrl', { url: url, fileName: 'cypress/fixtures/' + fileName });
      });
  });
});
