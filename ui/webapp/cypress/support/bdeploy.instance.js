Cypress.Commands.add('createInstance', function(group, name, version = '2.0.0') {
  cy.visit('/');
  cy.get('input[hint=Filter]').type(group);
  cy.get('[data-cy=group-' + group + ']').first().click();

  // wait until the progress spinner disappears
  cy.waitUntilContentLoaded();

  // attention: the button contains 'add' not '+' (mat-icon('add') = '+')
  cy.contains('button', 'add').click();

  // do "something"/"a check" before typing, some times we got "st Instance..." or "est Instance..." instead of "Test Instance..."
  // looks as if typing starts before angular is ready and angular resets the input?
  cy.contains('button', 'SAVE').should('exist').and('be.disabled');
  cy.get('[placeholder=Name]').type(name)
  cy.get('[placeholder=Description]').type('Test Instance for automated test')

  // angular drop down is something very different from a native HTML select/option
  cy.get('[placeholder=Purpose]').click()
  cy.get('mat-option').contains('TEST').click()

  cy.get('[placeholder=Product]').click()
  cy.get('mat-option').contains('Demo Product').click();

  cy.get('[placeholder=Version]').click();
  cy.get('mat-option').contains(version).click();

  return cy.get('mat-toolbar-row').contains('UUID').get('b').then(el => {
    cy.get('button').contains('SAVE').click();
    return cy.wrap(el.text())
  });

})

Cypress.Commands.add('deleteInstance', function(group, instanceUuid) {
  // make sure we're on the correct page :) this allows delete to work if previous tests failed.
  cy.visit('/#/instance/browser/' + group)

  // open the menu on the card
  cy.contains('mat-card', instanceUuid).clickContextMenuItem('Delete')

  // place a trigger on the endpoint, so we can later wait for it
  cy.server()
  cy.route('GET', '/api/group/Test/instance').as('reload')

  // in the resulting dialog, click OK
  cy.get('mat-dialog-container').contains('button', 'OK').click();

  // wait for the dialog to disappear and the page to reload
  cy.waitUntilContentLoaded();

  // now NO trace of the UUID should be left.
  cy.get('body').contains(instanceUuid).should('not.exist');
})

Cypress.Commands.add('gotoInstance', function(groupName, instanceUuid) {
   cy.visit('/');
   cy.get('[data-cy=group-' + groupName + ']').first().click();
   cy.waitUntilContentLoaded();
   cy.get('[data-cy=instance-' + instanceUuid + ']').first().click();
})

Cypress.Commands.add('installAndActivate', {prevSubject: true}, (subject) => {
  cy.get('mat-loading-spinner').should('not.exist');

  // should be in the instance version list now, install
  cy.wrap(subject).clickContextMenuItem('Install')

  // wait for progress and the icon to appear
  cy.wrap(subject).waitUntilContentLoaded()
  cy.wrap(subject).contains('mat-icon', 'check_circle_outline').should('exist')

  // activate the installed instance version
  return cy.wrap(subject).activate();
})

Cypress.Commands.add('activate', {prevSubject: true}, (subject) => {
  cy.get('mat-loading-spinner').should('not.exist');

  // activate the installed instance version
  cy.wrap(subject).clickContextMenuItem('Activate')

  // wait for progress and the icon to appear
  cy.wrap(subject).waitUntilContentLoaded()
  cy.wrap(subject).contains('mat-icon', 'check_circle').should('exist')

  // no error should have popped up.
  cy.get('snack-bar-container').should('not.exist')
  return cy.wrap(subject);
})

Cypress.Commands.add('closeConfigureApplications', function() {
  cy.get('body').then($body => {
    if($body.find('mat-toolbar:contains("close")').length > 0) {
      cy.contains('button', 'close').click();
    }
  })
})

Cypress.Commands.add('getLatestInstanceVersion', function() {
  cy.contains('mat-toolbar', 'Instance Versions').should('exist').and('be.visible')
  return cy.get('app-instance-version-card').first()
})

Cypress.Commands.add('getActiveInstanceVersion', function() {
  cy.get('body').then($body => {
    if($body.find('mat-toolbar:contains("close")').length > 0) {
      cy.contains('button', 'close').click();
    }
  })

  return cy.get('mat-card[data-cy=active]').should('have.length', 1).closest('app-instance-version-card')
})

Cypress.Commands.add('addAndSetOptionalParameter', function(panel, name, value) {
  cy.contains('mat-expansion-panel', panel).as('panel');

  cy.get('@panel').click();
  cy.get('@panel').contains('button', 'Manage Optional').click();

  cy.get('[placeholder=Filter').type(name);

  cy.get('mat-dialog-container').contains('td', name).closest('tr').find('mat-checkbox').click();
  cy.get('mat-dialog-container').contains('button', 'Save').click();

  cy.get('@panel').find('[placeholder="' + name + '"]').should('exist')
  cy.get('@panel').find('[placeholder="' + name + '"]').clear().type(value, { parseSpecialCharSequences: false })
})

Cypress.Commands.add('convertMissingToCustomParameter', function(id, name) {
  cy.contains('mat-expansion-panel', 'Unknown Parameters').as('panel');
  cy.get('@panel').contains('mat-grid-tile', name).contains('button','save_alt').click();

  cy.contains('mat-expansion-panel', 'Custom Parameters').as('panel');
  cy.get('@panel').click();
  cy.get('@panel').find('[placeholder="' + id + '.custom"]').should('exist')
})

Cypress.Commands.add('deleteMissingParameter', function(name) {
  cy.contains('mat-expansion-panel', 'Unknown Parameters').as('panel');

  cy.get('@panel').click();
  cy.get('@panel').contains('mat-grid-tile', name).contains('button','delete').click();
})

Cypress.Commands.add('createNewInstanceVersionByDummyChange', function(instanceGroupName, instanceUuid, nodeName, applicationName) {
  cy.gotoInstance(instanceGroupName, instanceUuid);
  cy.getApplicationConfigCard(nodeName, applicationName).clickContextMenuItem('Configure...')

  // toggle 'Keep Alive' slider
  cy.contains('div', 'Keep Alive').find('.mat-slide-toggle-thumb').first().click();

  cy.contains('button', 'APPLY').click();
  cy.getApplicationConfigCard(nodeName, applicationName).find('.app-config-modified').should('exist')
  cy.contains('button', 'SAVE').click();
})
