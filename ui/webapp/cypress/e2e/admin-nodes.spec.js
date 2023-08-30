//@ts-check

describe('Admin Nodes Test', () => {
  beforeEach(() => {
    cy.login();
  });

  it('Tests Master Node', () => {
    cy.visit('/');
    cy.get('.local-hamburger-button').click();
    cy.get('button[data-cy=Administration]').click();

    cy.contains('a', 'Nodes').click();
    cy.inMainNavContent(() => {
      cy.contains('tr', 'master').should('exist').click();
    });

    cy.waitUntilContentLoaded();
    cy.screenshot('Doc_Admin_Nodes_Details');

    cy.inMainNavFlyin('app-node-details', () => {
      cy.get('button[data-cy^=Edit]').should('be.disabled');
      cy.get('button[data-cy^=Apply]').should('be.disabled');
      cy.get('button[data-cy^=Remove]').should('be.disabled');

      cy.get('button[data-cy^=Convert]').click();
    });

    cy.inMainNavFlyin('app-node-conversion', () => {
      cy.contains('Drag me to the target server').should('exist');
    });

    cy.screenshot('Doc_Admin_Nodes_Conversion');

    cy.inMainNavFlyin('app-node-conversion', () => {
      cy.pressToolbarButton('Back to Overview');
    });

    cy.inMainNavFlyin('app-node-details', () => {
      cy.pressToolbarButton('Close');
    });

    cy.checkMainNavFlyinClosed();
  });

  it('Tests Add/Remove', () => {
    cy.visit('/');
    cy.get('.local-hamburger-button').click();
    cy.get('button[data-cy=Administration]').click();

    cy.contains('a', 'Nodes').click();
    cy.pressToolbarButton('Add Node');

    cy.waitUntilContentLoaded();
    cy.screenshot('Doc_Admin_Nodes_Add');

    cy.intercept({ method: 'GET', url: '**/api/node-admin/nodes' }).as('list');

    cy.inMainNavFlyin('app-add-node', () => {
      cy.fillFormInput('name', 'TestNode');
      cy.fillFormInput('uri', 'Dummy');
      cy.fillFormInput('auth', 'Dummy');
      cy.contains('button', 'Save').click();
    });

    cy.checkMainNavFlyinClosed();
    cy.wait('@list');

    cy.inMainNavContent(() => {
      cy.contains('tr', 'TestNode').should('exist').click();
    });

    cy.inMainNavFlyin('app-node-details', () => {
      cy.get('button[data-cy^=Edit]').should('be.enabled');
      cy.get('button[data-cy^=Apply]').should('be.disabled');
      cy.get('button[data-cy^=Remove]').should('be.enabled').click();

      cy.contains('app-bd-notification-card', 'Remove TestNode').within(() => {
        cy.get('button[data-cy^=Yes]').click();
      });
    });

    cy.wait('@list');

    cy.inMainNavContent(() => {
      cy.contains('tr', 'TestNode').should('not.exist');
    });
  });
});
