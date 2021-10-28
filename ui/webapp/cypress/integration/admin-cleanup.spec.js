//@ts-check

describe('Admin UI Tests (Cleanup)', () => {

  var groupName = 'Demo';

  beforeEach(() => {
    cy.login();
  });

  it('Prepares the test (group, products, instance)', () => {
    cy.visit('/');
    cy.createGroup(groupName);
    cy.uploadProductIntoGroup(groupName, 'test-product-1-direct.zip');
    cy.uploadProductIntoGroup(groupName, 'test-product-2-direct.zip');
  });

  it('Tests Cleanup', () => {
    cy.visit('/');

    cy.get('.local-hamburger-button').click();
    cy.get('button[data-cy=Administration]').click();

    cy.contains('a', 'Manual Cleanup').click();

    cy.screenshot('Doc_Cleanup');

    cy.inMainNavContent(() => {
      cy.intercept({ method: 'GET', url: '/api/cleanUi' }).as('cleanup');
      cy.pressToolbarButton('Calculate');
      cy.wait('@cleanup');
      cy.get('mat-tab-group').find('tr').should('have.length.at.least', 5);
    });

    cy.screenshot('Doc_Cleanup_Actions');

    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Abort Cleanup');
      cy.contains('tr', 'DELETE_MANIFEST').should('not.exist');
    });

    cy.inMainNavContent(() => {
      cy.intercept({ method: 'GET', url: '/api/cleanUi' }).as('cleanup');
      cy.pressToolbarButton('Calculate');
      cy.wait('@cleanup');
      cy.get('mat-tab-group').find('tr').should('have.length.at.least', 5);

      cy.pressToolbarButton('Perform');
      cy.wait('@cleanup');
      cy.contains('tr', 'DELETE_MANIFEST').should('not.exist');
    });

  });

  it('Cleans up', () => {
    cy.deleteGroup(groupName);
  });

});
