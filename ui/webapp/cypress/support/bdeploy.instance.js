Cypress.Commands.add('createInstance', function(name) {
  cy.visit('/');
  cy.get('input[hint=Filter]').type('Test');
  cy.get('[data-cy=group-Test]').first().click();

  // wait until the progress spinner disappears
  cy.get('mat-progress-spinner').should('not.exist')

  // attention: the button contains 'add' not '+' (mat-icon('add') = '+')
  cy.contains('button', 'add').click();

  cy.get('[placeholder=Name]').type(name)
  cy.get('[placeholder=Description]').type('Test Instance for automated test')

  // angular drop down is something very different from a native HTML select/option
  cy.get('[placeholder=Purpose]').click()
  cy.get('mat-option').contains('TEST').click()

  cy.get('[placeholder=Product]').click()
  cy.get('mat-option').contains('demo/product').click();

  cy.get('[placeholder=Version]').click();
  cy.get('mat-option').contains('2.0.0').click();

  // finally the target, which is the configured backend with the configured token.
  cy.get('[placeholder="Master URL"]').type(Cypress.env('backendBaseUrl'))

  cy.get('body').then($body => {
    if($body.find('mat-option:contains(localhost)').length) {
      cy.contains('mat-option', 'localhost').click();
    } else {
      cy.fixture('token.json').then(fixture => {
        // don't use .type(fixture.token) as this mimiks a typing user (delay)
        cy.get('[placeholder="Security Token"]').invoke('val', fixture.token).trigger('input')
      })
    }
  })

  return cy.get('mat-toolbar-row').contains('UUID').get('b').then(el => {
    cy.get('button').contains('SAVE').click();
    return cy.wrap(el.text())
  });

})

Cypress.Commands.add('getLatestInstanceVersion', function() {
  cy.contains('mat-toolbar', 'Instance Versions').should('exist').and('be.visible')
  return cy.get('app-instance-version-card').first()
})

Cypress.Commands.add('getActiveInstanceVersion', function() {
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
  cy.get('@panel').find('[placeholder="' + name + '"]').clear().type(value)
})
