//@ts-check

describe('Admin UI Tests (BHive)', () => {
  beforeEach(() => {
    cy.login();
  });

  it('Creates Group BHives and re-organize pool', () => {
    cy.visit('/');
    cy.createGroup('DemoA');
    cy.visit('/');
    cy.createGroup('DemoB');

    cy.visit('/');
    cy.uploadProductIntoGroup('DemoA', 'test-product-2-direct.zip');
    cy.visit('/');
    cy.uploadProductIntoGroup('DemoB', 'test-product-2-direct.zip');

    cy.visit('/');
    cy.get('.local-hamburger-button').click();
    cy.get('button[data-cy=Administration]').click();

    cy.contains('a', 'Jobs').click();

    cy.screenshot('Doc_Admin_Jobs');
    cy.inMainNavContent(() => {
      cy.contains('tr', 'Pool Re-organization').within(() => {
        cy.contains('button', 'play_arrow').click();
      });

      cy.waitUntilContentLoaded();

      cy.get('mat-snack-bar-container').should('not.exist');
    });
  });

  it('Tests BHive Admin UI', () => {
    cy.visit('/');
    cy.get('.local-hamburger-button').click();
    cy.get('button[data-cy=Administration]').click();

    cy.contains('a', 'BHives').click();

    cy.waitUntilContentLoaded();
    cy.screenshot('Doc_Admin_BHive_Browser');
    cy.inMainNavContent(() => {
      cy.contains('tr', 'default').click();
    });

    cy.screenshot('Doc_Admin_BHive_Details');
    cy.inMainNavFlyin('app-bhive-details', () => {
      cy.get('button[data-cy^="Browse Logs"]').click();
    });

    cy.inMainNavFlyin('app-bhive-log-browser', () => {
      cy.get('tr').should('have.length.above', 1);

      cy.pressToolbarButton('Back');
    });

    cy.inMainNavFlyin('app-bhive-details', () => {
      cy.get('button[data-cy^="Browse Contents"]').click();
    });

    cy.inMainNavFlyin('app-bd-bhive-browser', () => {
      cy.contains('tr', 'meta/minions').click();

      cy.get('app-bd-dialog-toolbar').within(() => {
        cy.get('button[data-cy^="Back to Parent"]').should('exist').and('be.enabled');
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
