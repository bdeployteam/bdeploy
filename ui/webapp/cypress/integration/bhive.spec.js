describe('BHive Browser Tests', function() {
  beforeEach(() => {
    cy.login();
  })

  it('Check user manifest in default hive', function() {
    cy.visit('/');
    cy.get('input[hint=Filter]').type('Test');
    cy.get('[data-cy=group-Test]').should('have.length', 1).click();

    cy.visit('/#/hive/browser')

    // wait for at least one row, meaning the pre-selected hive has been loaded.
    cy.get('tbody>tr').should('exist');

    // all in one chain to re-try the click on the drop down if it does not open.
    cy.get('mat-select[placeholder="Select a Hive"]').should('have.length', 1).click().get('body').contains('mat-option', 'default').should('exist').click();

    cy.get('td:contains("users/admin")').should('be.visible').and('have.length.greaterThan', 1).contains('td', 'users/admin:1').click();
    cy.get('td:contains("user.json")').should('have.length', 1);

    cy.contains('mat-chip', 'home').click();

    cy.get('td:contains("users/admin")').should('be.visible').and('have.length.greaterThan', 1)
  })

  it('BHive FSCK', function() {
    cy.contains('button', 'bug_report').click();
    cy.contains('snack-bar-container', '0 damaged').should('exist').contains('button', 'DISMISS').click();
  })

  it('BHive Repair', function() {
    cy.contains('button', 'build').click();
    cy.contains('snack-bar-container', '0 damaged').should('exist').contains('button', 'DISMISS').click();
  })

  it('BHive Prune', function() {
    cy.contains('button', 'delete_sweep').click();
    cy.contains('snack-bar-container', 'Prune freed').should('exist').contains('button', 'DISMISS').click();
  })
})
