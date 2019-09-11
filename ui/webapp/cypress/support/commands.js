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

import './bdeploy.login'
import './bdeploy.instance'
import './bdeploy.node'
import './dragdrop'

Cypress.Commands.add('clickContextMenuItem', { prevSubject: true}, function(subject, item) {
  let wrapped = cy.wrap(subject);

  wrapped.find('button').contains('more_vert').click();
  cy.get('[role=menuitem]').contains(item).should('be.enabled').click();

  return wrapped;
})
