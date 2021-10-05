//@ts-check

describe('Admin UI Tests (Accounts)', () => {
  beforeEach(() => {
    cy.login();
  });

  it('Tests Account Settings', () => {
    cy.visit('/');
    cy.get('.local-hamburger-button').click();
    cy.get('button[data-cy=Administration]').click();

    cy.contains('a', 'User Accounts').click();
    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.fixture('login.json').then((user) => {
        cy.contains('tr', user.user)
          .should('exist')
          .within(() => {
            cy.contains('ADMIN').should('exist');
          })
          .click();
      });
    });

    cy.inMainNavFlyin('app-user-admin-detail', () => {
      cy.contains('editing restricted').should('exist');
      cy.contains('tr', 'Global')
        .should('exist')
        .within(() => {
          cy.contains('ADMIN').should('exist');
          cy.get('button[data-cy^=Delete]').click();
        });

      // cannot remove global permission on self
      cy.contains('app-bd-notification-card', 'Cannot remove').within(() => {
        cy.get('button[data-cy^=OK]').click();
      });
    });

    // create a test user
    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Create User');
      cy.intercept({ method: 'PUT', url: '/api/auth/admin/local' }).as('createUser');

      cy.contains('app-bd-notification-card', 'Add User').within(() => {
        cy.fillFormInput('name', 'test');
        cy.fillFormInput('fullName', 'Test User');
        cy.fillFormInput('email', 'example@example.org');
        cy.fillFormInput('pass', 'pass');
        cy.fillFormInput('passConfirm', 'pass');

        cy.get('button[data-cy="OK"]').should('be.enabled').click();
      });

      cy.wait('@createUser');
      cy.waitUntilContentLoaded();

      cy.contains('tr', 'test')
        .should('exist')
        .within(() => {
          cy.contains('mat-icon', 'check_box_outline_blank');
        })
        .click();
    });

    // deactivate test user
    cy.inMainNavFlyin('app-user-admin-detail', () => {
      cy.contains('test').should('exist');
      cy.contains('Test User').should('exist');
      cy.contains('example@example.org').should('exist');

      cy.contains('No data to show').should('exist');

      cy.get('button[data-cy^="Deactivate"]').click();
    });

    // check deactivation
    cy.waitUntilContentLoaded();
    cy.inMainNavContent(() => {
      cy.contains('tr', 'test')
        .should('exist')
        .within(() => {
          cy.contains('mat-icon', 'check_box').should('exist');
        });
    });

    // activate test user
    cy.inMainNavFlyin('app-user-admin-detail', () => {
      cy.contains('INACTIVE').should('exist');
      cy.get('button[data-cy^="Activate"]').click();
    });

    // check activation
    cy.waitUntilContentLoaded();
    cy.inMainNavContent(() => {
      cy.contains('tr', 'test')
        .should('exist')
        .within(() => {
          cy.contains('mat-icon', 'check_box_outline_blank').should('exist');
        });
    });

    // set global permission.
    cy.inMainNavFlyin('app-user-admin-detail', () => {
      cy.get('button[data-cy^="Assign Permission"]').click();

      cy.contains('app-bd-notification-card', 'Assign Permission').within(() => {
        cy.fillFormSelect('permission', 'READ');
        cy.get('button[data-cy^="OK"]').click();
      });
    });

    // check READ perm.
    cy.waitUntilContentLoaded();
    cy.inMainNavContent(() => {
      cy.contains('tr', 'test')
        .should('exist')
        .within(() => {
          cy.contains('READ').should('exist');
        });
    });

    cy.inMainNavFlyin('app-user-admin-detail', () => {
      cy.contains('tr', 'Global')
        .should('exist')
        .within(() => {
          cy.contains('READ').should('exist');
        });
    });

    cy.intercept({ method: 'GET', url: '/api/auth/admin/users' }).as('listUsers');

    // set global permission.
    cy.inMainNavFlyin('app-user-admin-detail', () => {
      cy.get('button[data-cy^="Assign Permission"]').click();

      cy.contains('app-bd-notification-card', 'Assign Permission').within(() => {
        cy.fillFormSelect('permission', 'ADMIN');
        cy.get('button[data-cy^="OK"]').click();
      });
    });

    // need to wait for the update to arrive
    cy.wait('@listUsers');
    cy.waitUntilContentLoaded();

    // check ADMIN perm.
    cy.inMainNavContent(() => {
      cy.contains('tr', 'test')
        .should('exist')
        .within(() => {
          cy.contains('ADMIN').should('exist');
        });
    });

    cy.inMainNavFlyin('app-user-admin-detail', () => {
      cy.contains('tr', 'Global')
        .should('exist')
        .within(() => {
          cy.contains('ADMIN').should('exist');
        });
    });

    // check edit
    cy.inMainNavFlyin('app-user-admin-detail', () => {
      cy.get('button[data-cy^="Edit"]').click();
      cy.contains('app-bd-notification-card', 'Edit User').within(() => {
        cy.fillFormInput('fullName', 'Different User');
        cy.fillFormInput('email', 'new@example.org');

        cy.get('button[data-cy^="OK"]').click();
      });

      cy.contains('Different User').should('exist');
      cy.contains('new@example.org').should('exist');
    });

    // clean up test user in the end.
    cy.inMainNavFlyin('app-user-admin-detail', () => {
      cy.get('button[data-cy^="Delete User"]').click();
    });

    cy.contains('app-bd-notification-card', 'Delete User').within(() => {
      cy.get('button[data-cy^=YES]').click();
    });
  });
});
