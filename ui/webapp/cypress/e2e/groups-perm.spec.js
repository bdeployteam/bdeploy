//@ts-check

describe('Groups Tests (Permissions)', () => {
  var groupName = 'Demo';
  var instanceName = 'TestInstance';

  before(() => {
    cy.authenticatedRequest({
      method: 'DELETE',
      url: `${Cypress.config('baseUrl')}/api/auth/admin?name=read`,
      failOnStatusCode: false,
    });
    cy.authenticatedRequest({
      method: 'DELETE',
      url: `${Cypress.config('baseUrl')}/api/auth/admin?name=write`,
      failOnStatusCode: false,
    });
  });

  after(() => {
    cy.authenticatedRequest({
      method: 'DELETE',
      url: `${Cypress.config('baseUrl')}/api/auth/admin?name=read`,
      failOnStatusCode: false,
    });
    cy.authenticatedRequest({
      method: 'DELETE',
      url: `${Cypress.config('baseUrl')}/api/auth/admin?name=write`,
      failOnStatusCode: false,
    });
  });

  beforeEach(() => {
    cy.login();
  });

  it('Creates a group', () => {
    cy.visit('/');
    cy.createGroup(groupName);
    cy.uploadProductIntoGroup(groupName, 'test-product-2-direct.zip');
  });

  it('Creates Test Users', () => {
    cy.visit('/');
    cy.get('.local-hamburger-button').click();
    cy.get('button[data-cy=Administration]').click();

    cy.contains('a', 'User Accounts').click();

    // create test users - CLIENT permission has its separate test.
    ['read', 'write'].forEach((perm) => {
      cy.pressToolbarButton('Create User');
      cy.intercept({ method: 'PUT', url: '/api/auth/admin/local' }).as('createUser');
      cy.inMainNavFlyin('app-add-user', () => {
        cy.fillFormInput('name', perm);
        cy.fillFormInput('fullName', `${perm} User`);
        cy.fillFormInput('email', 'example@example.org');
        cy.fillFormInput('pass', perm.repeat(3));
        cy.fillFormInput('passConfirm', perm.repeat(3));
        cy.get('button[data-cy="Save"]').should('be.enabled').click();
      });

      cy.wait('@createUser');

      cy.inMainNavContent(() => {
        cy.contains('tr', perm).should('exist').click();
      });

      // set global permission.
      cy.inMainNavFlyin('app-user-admin-detail', () => {
        cy.get('button[data-cy^="Assign Permission"]').click();
      });

      cy.waitForApi(() => {
        cy.inMainNavFlyin('app-user-assign-permission', () => {
          cy.fillFormSelect('permission', perm.toUpperCase());
          cy.get('button[data-cy="Save"]').should('be.enabled').click();
        });
      });

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

  it('Tests write user can create instance', () => {
    cy.visit('/');
    cy.pressMainNavTopButton('User Settings');
    cy.inMainNavFlyin('app-settings', () => {
      cy.get('button[data-cy="Logout"]').click();
    });
    cy.waitUntilContentLoaded();
    cy.fillFormInput('user', 'write');
    cy.fillFormInput('pass', 'write'.repeat(3));

    cy.get('button[type="submit"]').click();
    cy.contains('tr', groupName).should('exist');

    cy.inMainNavContent(() => {
      // adding instance group requires admin permissions.
      cy.get('button[data-cy^="Add Instance Group"]').should('be.disabled');
    });

    cy.visit('/');
    cy.createInstance(groupName, instanceName, 'Demo Product', '2.0.0');
  });

  it('Tests read user can not create instance', () => {
    cy.visit('/');
    cy.pressMainNavTopButton('User Settings');
    cy.inMainNavFlyin('app-settings', () => {
      cy.get('button[data-cy="Logout"]').click();
    });
    cy.waitUntilContentLoaded();
    cy.fillFormInput('user', 'read');
    cy.fillFormInput('pass', 'read'.repeat(3));

    cy.get('button[type="submit"]').click();
    cy.contains('tr', groupName).should('exist');

    cy.inMainNavContent(() => {
      // adding instance group requires admin permissions.
      cy.get('button[data-cy^="Add Instance Group"]').should('be.disabled');
    });

    cy.visit('/');
    cy.enterGroup(groupName);

    cy.inMainNavContent(() => {
      cy.get('button[data-cy="Add Instance..."]').should('be.disabled');
    });
  });

  it('Tests assigning local write permissions', () => {
    cy.visit('/');
    cy.enterGroup(groupName);
    cy.pressToolbarButton('Group Settings');

    cy.screenshot('Doc_GroupSettings');

    cy.inMainNavFlyin('app-settings', () => {
      cy.get('button[data-cy="Instance Group Permissions"]').click();
    });

    cy.waitUntilContentLoaded();
    cy.screenshot('Doc_GroupPermGlobalOnly');

    cy.inMainNavFlyin('app-instance-group-permissions', () => {
      cy.contains('tr', 'read')
        .should('exist')
        .within(() => {
          cy.get('button[data-cy^="Modify"]').click();
        });

      cy.contains('app-bd-notification-card', 'Modify')
        .should('exist')
        .within(() => {
          cy.fillFormSelect('modPerm', 'WRITE');
        });
    });

    cy.screenshot('Doc_GroupPermSetWrite');

    cy.inMainNavFlyin('app-instance-group-permissions', () => {
      cy.contains('app-bd-notification-card', 'Modify')
        .should('exist')
        .within(() => {
          cy.get('button[data-cy^="OK"]').click();
        });
    });

    cy.screenshot('Doc_GroupPermAssigned');
  });

  it('Tests read user with local permission can create instance', () => {
    cy.visit('/');
    cy.pressMainNavTopButton('User Settings');
    cy.inMainNavFlyin('app-settings', () => {
      cy.get('button[data-cy="Logout"]').click();
    });
    cy.waitUntilContentLoaded();
    cy.fillFormInput('user', 'read');
    cy.fillFormInput('pass', 'read'.repeat(3));

    cy.get('button[type="submit"]').click();
    cy.contains('tr', groupName).should('exist');

    cy.visit('/');
    cy.createInstance(groupName, instanceName + '2', 'Demo Product', '2.0.0');
  });
});
