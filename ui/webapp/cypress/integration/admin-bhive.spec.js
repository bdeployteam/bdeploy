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
      cy.get('button[data-cy^="Audit"]').click();
    });

    cy.inMainNavFlyin('app-bhive-audit', () => {
      cy.waitUntilContentLoaded();
      cy.get('tr').should('have.length.above', 1);

      cy.pressToolbarButton('Back');
    });

    cy.inMainNavFlyin('app-bhive-details', () => {
      cy.get('button[data-cy^="Browse"]').click();
    });

    cy.inMainNavFlyin('app-bhive-browser', () => {
      cy.waitUntilContentLoaded();
      cy.contains('tr', 'meta/minions').click();

      cy.waitUntilContentLoaded();
      cy.get('app-bd-dialog-toolbar').within(() => {
        cy.get('button[data-cy^="Back to Parent"]').should('exist').and('be.enabled');
      });

      cy.contains('tr', 'minion.json').click();
      cy.contains('app-bd-notification-card', 'Preview').within(() => {
        cy.get('button[data-cy^="CLOSE"]').click();
      });

      cy.pressToolbarButton('Back to Overview');
    });

    cy.inMainNavFlyin('app-bhive-details', () => {
      cy.intercept({ method: 'GET', url: '/api/hive/fsck?hive=default&fix=true' }).as('fsck');
      cy.get('button[data-cy^="Repair"]').click();
      cy.contains('app-bd-notification-card', 'Repair').within(() => {
        cy.get('button[data-cy^="YES"]').click();
      });
      cy.wait('@fsck');

      cy.contains('app-bd-notification-card', 'Repair').within(() => {
        cy.contains('No damaged objects').should('exist');
        cy.get('button[data-cy^="OK"]').click();
      });
    });

    cy.inMainNavFlyin('app-bhive-details', () => {
      cy.intercept({ method: 'GET', url: '/api/hive/prune?hive=default' }).as('prune');
      cy.get('button[data-cy^="Prune"]').click();
      cy.wait('@prune');

      cy.contains('app-bd-notification-card', 'Prune').within(() => {
        cy.get('button[data-cy^="OK"]').click();
      });
    });
  });
});
