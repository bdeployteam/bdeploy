describe('Central/Managed Basic Test', function() {
  it('Visit Central Server', function() {
    cy.visitCentral('/');
    cy.contains('mat-toolbar', 'CENTRAL').within(() => {
      cy.contains('button', 'menu').click();
    })
    cy.contains('mat-sidenav', 'CENTRAL').should('exist').and('be.visible');
  })

  it('Visit Managed Server', function() {
    cy.visitManaged('/');
    cy.contains('mat-toolbar', 'MANAGED').within(() => {
      cy.contains('button', 'menu').click();
    })
    cy.contains('mat-sidenav', 'MANAGED').should('exist').and('be.visible');
  })
})
