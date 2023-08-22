//@ts-check

describe('Admin UI Tests (BHive)', () => {
  beforeEach(() => {
    cy.login();
  });

  it('Tests BHive Admin UI', () => {
    cy.visit('/');
    cy.get('.local-hamburger-button').click();
    cy.get('button[data-cy=Administration]').click();

    cy.contains('a', 'BHives').click();
    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.contains('tr', 'default').click();
    });

    cy.inMainNavFlyin('app-bhive-details', () => {
      cy.get('button[data-cy^="Browse Logs"]').click();
    });

    cy.inMainNavFlyin('app-bhive-log-browser', () => {
      cy.waitUntilContentLoaded();
      cy.get('tr').should('have.length.above', 1);

      cy.pressToolbarButton('Back');
    });

    cy.inMainNavFlyin('app-bhive-details', () => {
      cy.get('button[data-cy^="Browse Contents"]').click();
    });

    cy.inMainNavFlyin('app-bhive-browser', () => {
      cy.waitUntilContentLoaded();
      cy.contains('tr', 'meta/minions').click();

      cy.waitUntilContentLoaded();
      cy.get('app-bd-dialog-toolbar').within(() => {
        cy.get('button[data-cy^="Back to Parent"]')
          .should('exist')
          .and('be.enabled');
      });

      cy.contains('tr', 'minion.json').click();
      cy.contains('app-bd-notification-card', 'Preview').within(() => {
        cy.get('button[data-cy^="Close"]').click();
      });

      cy.pressToolbarButton('Back to Overview');
    });

    cy.inMainNavFlyin('app-bhive-details', () => {
      cy.intercept({
        method: 'GET',
        url: '/api/hive/repair-and-prune?hive=default&fix=true',
      }).as('repairAndPrune');
      cy.get('button[data-cy^="Repair and Prune Unused Objects"]').click();
      cy.contains('app-bd-notification-card', 'Repair and Prune').within(() => {
        cy.get('button[data-cy^="Yes"]').click();
      });
      cy.wait('@repairAndPrune');

      cy.contains('app-bd-notification-card', 'Repair and Prune').within(() => {
        cy.contains('No damaged objects').should('exist');
        cy.contains('Prune freed').should('exist');
        cy.get('button[data-cy^="OK"]').click();
      });
    });
  });
});
