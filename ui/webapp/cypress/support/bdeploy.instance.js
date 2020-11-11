Cypress.Commands.add('createInstance', function (group, name, mode = 'STANDALONE', version = '2.0.0', server = null) {
  cy.visitBDeploy('/', mode);
  cy.get('input[hint=Filter]').type(group);
  cy.get('[data-cy=group-' + group + ']')
    .first()
    .click();

  // wait until the progress spinner disappears
  cy.waitUntilContentLoaded();

  // attention: the button contains 'add' not '+' (mat-icon('add') = '+')
  cy.contains('button', 'add').click();
  cy.get('[data-placeholder=Name]').type(name);
  cy.get('[data-placeholder=Description]').type('Test Instance for automated test');

  // angular drop down is something very different from a native HTML select/option
  cy.get('[placeholder=Purpose]').click();
  cy.get('mat-option').contains('TEST').click();

  // in case products have not yet been fully loaded.
  cy.waitUntilContentLoaded();

  cy.get('[placeholder=Product]').click();
  cy.get('mat-option').contains('Demo Product').click();

  cy.get('[placeholder=Version]').click();
  cy.get('mat-option').contains(version).click();

  if (mode === 'CENTRAL' && server) {
    cy.get('[placeholder="Managed Server"]').click();
    cy.get('mat-option').contains(server).click();
  }

  return cy
    .get('mat-toolbar-row')
    .contains('UUID')
    .get('strong')
    .then((el) => {
      const uuid = el.text();
      cy.contains('button', 'SAVE').click();
      cy.waitUntilContentLoaded();
      return cy.wrap(uuid);
    });
});

Cypress.Commands.add('deleteInstance', function (group, instanceUuid, mode = 'STANDALONE') {
  // make sure we're on the correct page :) this allows delete to work if previous tests failed.
  cy.visitBDeploy('/#/instance/browser/' + group, mode);

  // open the menu on the card
  cy.contains('mat-card', instanceUuid).clickContextMenuDialog('Delete', 'Delete Instance');

  // place a trigger on the endpoint, so we can later wait for it
  cy.server();
  cy.route('GET', '/api/group/Test/instance').as('reload');

  // in the resulting dialog, click OK
  cy.get('mat-dialog-container').contains('button', 'OK').click();

  // wait for the dialog to disappear and the page to reload
  cy.waitUntilContentLoaded();

  // now NO trace of the UUID should be left.
  cy.get('body').contains(instanceUuid).should('not.exist');
});

Cypress.Commands.add('gotoInstance', function (groupName, instanceUuid, mode = 'STANDALONE') {
  cy.visitBDeploy('/', mode);
  cy.get('[data-cy=group-' + groupName + ']')
    .first()
    .click('top');
  cy.waitUntilContentLoaded();
  cy.get('[data-cy=instance-' + instanceUuid + ']')
    .first()
    .click('top');
});

Cypress.Commands.add('startProcess', function (node, app) {
  cy.getActiveInstanceVersion().contains('Version').click();
  cy.getApplicationConfigCard(node, app).click();

  cy.contains('mat-toolbar', 'Process Control').should('exist');
  cy.contains('mat-toolbar', app).should('exist');

  // start the process, show process list
  cy.contains('app-process-details', app).within(() => {
    cy.contains('button', 'play_arrow').should('be.enabled').click();
    cy.get('app-process-status').find('.app-process-running').should('exist');

    cy.contains('button', 'settings').should('be.enabled').click();
  });

  // check that at least one process entry exists
  cy.get('app-process-list').within(() => {
    cy.get('tbody>tr').should('exist');
  });

  // close process list
  cy.get('.cdk-overlay-backdrop').click('top', { force: true, multiple: true }); // click even if obstructed.
});

Cypress.Commands.add('installAndActivate', { prevSubject: true }, (subject) => {
  cy.get('mat-loading-spinner').should('not.exist');

  // should be in the instance version list now, install
  cy.wrap(subject).clickContextMenuAction('Install');

  // wait for progress and the icon to appear
  cy.wrap(subject).waitUntilContentLoaded();
  cy.wrap(subject).contains('mat-icon', 'check_circle_outline').should('exist');

  // activate the installed instance version
  return cy.wrap(subject).activate();
});

Cypress.Commands.add('activate', { prevSubject: true }, (subject) => {
  cy.get('mat-loading-spinner').should('not.exist');

  // activate the installed instance version
  cy.wrap(subject).clickContextMenuAction('Activate');

  // wait for progress and the icon to appear
  cy.wrap(subject).waitUntilContentLoaded();
  cy.wrap(subject).contains('mat-icon', 'check_circle').should('exist');

  // no error should have popped up.
  cy.get('snack-bar-container').should('not.exist');
  return cy.wrap(subject);
});

Cypress.Commands.add('closeConfigureApplications', function () {
  cy.get('body').then(($body) => {
    if ($body.find('mat-toolbar:contains("close")').length > 0) {
      cy.contains('button', 'close').click();
    }
  });
});

Cypress.Commands.add('getLatestInstanceVersion', function () {
  cy.contains('mat-toolbar', 'Instance Versions').should('exist').and('be.visible');
  return cy.get('app-instance-version-card').first();
});

Cypress.Commands.add('getActiveInstanceVersion', function () {
  cy.get('body').then(($body) => {
    if ($body.find('mat-toolbar:contains("close")').length > 0) {
      cy.contains('button', 'close').click();
    }
  });

  return cy.get('mat-card[data-cy=active]').should('have.length', 1).closest('app-instance-version-card');
});

Cypress.Commands.add('addAndSetOptionalParameter', function (panel, name, value) {
  cy.contains('mat-expansion-panel', panel).as('panel');

  cy.get('@panel').click();
  cy.get('@panel').contains('button', 'Manage Optional').click();

  cy.get('[data-placeholder=Filter').type(name);

  cy.get('mat-dialog-container').contains('td', name).closest('tr').find('mat-checkbox').click();
  cy.get('mat-dialog-container').contains('button', 'Save').click();

  cy.get('@panel')
    .find('[data-placeholder="' + name + '"]')
    .should('exist');
  cy.get('@panel')
    .find('[data-placeholder="' + name + '"]')
    .clear()
    .type(value, { parseSpecialCharSequences: false });
});

Cypress.Commands.add('convertMissingToCustomParameter', function (id, name) {
  cy.contains('mat-expansion-panel', 'Unknown Parameters').as('panel');
  cy.get('@panel').contains('mat-grid-tile', name).contains('button', 'save_alt').click();

  cy.contains('mat-expansion-panel', 'Custom Parameters').as('panel');
  cy.get('@panel').click();
  cy.get('@panel')
    .find('[data-placeholder="' + id + '.custom"]')
    .should('exist');
});

Cypress.Commands.add('deleteMissingParameter', function (name) {
  cy.contains('mat-expansion-panel', 'Unknown Parameters').as('panel');

  cy.get('@panel').click();
  cy.get('@panel').contains('mat-grid-tile', name).contains('button', 'delete').click();
});

Cypress.Commands.add('createNewInstanceVersionByDummyChange', function (
  instanceGroupName,
  instanceUuid,
  nodeName,
  applicationName,
  mode = 'STANDALONE'
) {
  cy.gotoInstance(instanceGroupName, instanceUuid, mode);
  cy.getApplicationConfigCard(nodeName, applicationName).clickContextMenuAction('Configure');

  // toggle 'Keep Alive' slider
  cy.contains('div', 'Keep Alive').find('.mat-slide-toggle-thumb').first().click();

  cy.contains('button', 'APPLY').click();
  cy.getApplicationConfigCard(nodeName, applicationName).find('.app-config-modified').should('exist');
  cy.contains('button', 'SAVE').click();
  cy.waitUntilContentLoaded();
});

Cypress.Commands.add('typeInRichEditor', function (text) {
  cy.get('.text-editor textarea:first').type(text);
});
