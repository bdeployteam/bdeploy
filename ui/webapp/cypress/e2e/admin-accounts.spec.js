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

      cy.pressToolbarButton('Close');
    });

    // create a test user
    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Create User');
      cy.intercept({ method: 'PUT', url: '/api/auth/admin/local' }).as(
        'createUser'
      );
    });

    cy.inMainNavFlyin('add-user', () => {
      cy.fillFormInput('name', 'test');
      cy.fillFormInput('fullName', 'Test User');
      cy.fillFormInput('email', 'example@example.org');
      cy.fillFormInput('pass', 'passpasspass');
      cy.fillFormInput('passConfirm', 'passpasspass');
    });

    cy.screenshot('Doc_Admin_User_Accounts_Add');

    cy.inMainNavFlyin('add-user', () => {
      cy.get('button[data-cy="Save"]').should('be.enabled').click();
    });

    cy.inMainNavContent(() => {
      cy.wait('@createUser');
      cy.waitUntilContentLoaded();
    });

    cy.screenshot('Doc_Admin_User_Accounts');

    cy.inMainNavContent(() => {
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

    cy.screenshot('Doc_Admin_User_Accounts_Inactive');

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
    });

    cy.inMainNavFlyin('assign-permission', () => {
      cy.fillFormSelect('permission', 'READ');
      cy.get('button[data-cy="Save"]').should('be.enabled').click();
    });

    // check READ perm.
    cy.waitUntilContentLoaded();
    cy.inMainNavContent(() => {
      cy.contains('tr', 'test')
        .should('exist')
        .within(() => {
          cy.contains('READ').should('exist');
        })
        .click();
    });

    // set global permission.
    cy.inMainNavFlyin('app-user-admin-detail', () => {
      cy.contains('tr', 'Global')
        .should('exist')
        .within(() => {
          cy.contains('READ').should('exist');
        });
      cy.get('button[data-cy^="Assign Permission"]').click();
    });

    cy.inMainNavFlyin('assign-permission', () => {
      cy.fillFormSelect('permission', 'ADMIN');
    });

    cy.screenshot('Doc_Admin_User_Accounts_Permissions_Add');

    cy.inMainNavFlyin('assign-permission', () => {
      cy.get('button[data-cy="Save"]').should('be.enabled').click();
    });

    // check ADMIN perm.
    cy.waitUntilContentLoaded();
    cy.inMainNavContent(() => {
      cy.contains('tr', 'test')
        .should('exist')
        .within(() => {
          cy.contains('ADMIN').should('exist');
        })
        .click();
    });

    // check edit
    cy.inMainNavFlyin('app-user-admin-detail', () => {
      cy.get('button[data-cy^="Edit"]').click();
    });

    cy.inMainNavFlyin('edit-user', () => {
      cy.fillFormInput('fullName', 'Different User');
      cy.fillFormInput('email', 'new@example.org');
      cy.get('button[data-cy="Apply"]').should('be.enabled').click();
    });

    cy.waitUntilContentLoaded();
    cy.screenshot('Doc_Admin_User_Accounts_Edit');

    cy.inMainNavContent(() => {
      cy.contains('tr', 'test')
        .should('exist')
        .within(() => {
          cy.contains('ADMIN').should('exist');
        })
        .click();
    });

    // clean up test user in the end.
    cy.inMainNavFlyin('app-user-admin-detail', () => {
      cy.contains('Different User').should('exist');
      cy.contains('new@example.org').should('exist');
      cy.get('button[data-cy^="Delete User"]').click();
    });

    cy.contains('app-bd-notification-card', 'Delete User').within(() => {
      cy.get('button[data-cy^=Yes]').click();
    });
  });
});
