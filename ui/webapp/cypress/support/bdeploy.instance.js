Cypress.Commands.add('createInstance', function(name) {
  cy.visit('/');
  cy.get('[cypress=group-Test]').first().click();

  // attention: the button contains 'add' not '+' (mat-icon('add') = '+')
  cy.get('button').contains('add').click();

  cy.get('[placeholder="Name"]').type(name)

  // angular drop down is something very different from a native HTML select/option
  cy.get('[placeholder="Purpose"]').click()
  cy.get('mat-option').contains('TEST').click()

  cy.get('[placeholder="Description"]').type('Test Instance for automated test')
  cy.get('[placeholder="Product"]').click()
  cy.get('mat-option').contains('demo/product').click();

  cy.get('[placeholder="Version"]').click();
  cy.get('mat-option').first().click();

  cy.get('[placeholder="Master URL"]').type(Cypress.env('backendBaseUrl'))

  cy.get('body').then($body => {
    if($body.find('mat-option').length) {
      cy.get('mat-option').contains('localhost').click();
    } else {
      cy.fixture('token.json').then(fixture => {
        cy.get('[placeholder="Security Token"]').type(fixture.token, { delay: 0 })
      })
    }
  })

  cy.get('button').contains('SAVE').click();
})
