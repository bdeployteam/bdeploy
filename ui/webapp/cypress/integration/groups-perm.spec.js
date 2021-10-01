//@ts-check

describe('Groups Tests (Permissions)', () => {
  var groupName = 'Demo';
  var instanceName = 'TestInstance';

  before(() => {
    cy.cleanAllGroups();

    cy.authenticatedRequest({ method: 'DELETE', url: `${Cypress.env('backendBaseUrl')}/auth/admin?name=read`, failOnStatusCode: false });
    cy.authenticatedRequest({ method: 'DELETE', url: `${Cypress.env('backendBaseUrl')}/auth/admin?name=write`, failOnStatusCode: false });
  });

  beforeEach(() => {
    cy.login();
  });

  it('Creates a group', () => {
    cy.visit('/');
    cy.createGroup(groupName);
    cy.uploadProductIntoGroup(groupName, 'test-product-2-direct.zip');
    cy.createInstance(groupName, instanceName, 'Demo Product', '2.0.0');
  });

  it('Creates Test Users', () => {
    cy.visit('/');
    cy.get('.local-hamburger-button').click();
    cy.get('button[data-cy=Administration]').click();

    cy.contains('a', 'User Accounts').click();
    cy.waitUntilContentLoaded();

    // create test users - CLIENT permission has its separate test.
    cy.inMainNavContent(() => {
      ['read', 'write'].forEach((perm) => {
        cy.pressToolbarButton('Create User');
        cy.intercept({ method: 'PUT', url: '/api/auth/admin/local' }).as('createUser');

        cy.contains('app-bd-notification-card', 'Add User').within(() => {
          cy.fillFormInput('name', perm);
          cy.fillFormInput('fullName', `${perm} User`);
          cy.fillFormInput('email', 'example@example.org');
          cy.fillFormInput('pass', perm);
          cy.fillFormInput('passConfirm', perm);

          cy.get('button[data-cy="OK"]').should('be.enabled').click();
        });

        cy.wait('@createUser');
      });

      cy.waitUntilContentLoaded();
    });

    ['read', 'write'].forEach((perm) => {
      cy.inMainNavContent(() => {
        cy.contains('tr', perm).should('exist').click();
      });

      // set global permission.
      cy.inMainNavFlyin('app-user-admin-detail', () => {
        cy.get('button[data-cy^="Assign Permission"]').click();

        cy.waitForApi(() => {
          cy.contains('app-bd-notification-card', 'Assign Permission').within(() => {
            cy.fillFormSelect('permission', perm.toUpperCase());
            cy.get('button[data-cy^="OK"]').click();
          });
        });
      });

      cy.waitUntilContentLoaded();

      // check assigned perm.
      cy.waitUntilContentLoaded();
      cy.inMainNavContent(() => {
        cy.contains('tr', perm)
          .should('exist')
          .within(() => {
            cy.contains(perm.toUpperCase()).should('exist');
          });
      });
    });
  });

  // Permission tests:
  // TODO check global permissions on instance group

  it('Cleans up', () => {
    cy.deleteGroup(groupName);
    cy.authenticatedRequest({ method: 'DELETE', url: `${Cypress.env('backendBaseUrl')}/auth/admin?name=read`, failOnStatusCode: false });
    cy.authenticatedRequest({ method: 'DELETE', url: `${Cypress.env('backendBaseUrl')}/auth/admin?name=write`, failOnStatusCode: false });
  });
});
