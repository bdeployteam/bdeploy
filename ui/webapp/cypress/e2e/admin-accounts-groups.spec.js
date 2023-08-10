function logout(cy) {
  cy.pressMainNavTopButton('User Settings');
  cy.inMainNavFlyin('app-settings', () => {
    cy.get('button[data-cy="Logout"]').click();
  });
  cy.waitUntilContentLoaded();
}

function login(cy, user, pass) {
  cy.fillFormInput('user', user);
  cy.fillFormInput('pass', pass);

  cy.get('button[type="submit"]').click();

  cy.waitUntilContentLoaded();

  cy.inMainNavContent(() => {
    cy.contains('Welcome to BDeploy').should('exist');
  });
}

describe('Admin UI Tests (User Groups)', () => {
  const userName = 'test-user';
  const password = 'cypressP@ssw0rd';
  const userGroupName = 'testUserGroup';

  it('Creates test user and test user group', () => {
    cy.login();

    cy.visit('/');
    cy.get('.local-hamburger-button').click();
    cy.get('button[data-cy=Administration]').click();

    // Create user
    cy.contains('a', 'User Accounts').click();
    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Create User');
      cy.intercept({ method: 'PUT', url: '/api/auth/admin/local' }).as(
        'createUser'
      );
    });

    cy.inMainNavFlyin('add-user', () => {
      cy.fillFormInput('name', userName);
      cy.fillFormInput('fullName', 'Test User');
      cy.fillFormInput('email', `${userName}@example.org`);
      cy.fillFormInput('pass', password);
      cy.fillFormInput('passConfirm', password);
      cy.get('button[data-cy="Save"]').should('be.enabled').click();
    });

    cy.inMainNavContent(() => {
      cy.wait('@createUser');
      cy.waitUntilContentLoaded();
    });

    // Create user group
    cy.contains('a', 'User Groups').click();
    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Create User Group');
      cy.intercept({ method: 'PUT', url: '/api/auth/admin/user-groups' }).as(
        'createUserGroup'
      );
    });

    cy.inMainNavFlyin('add-user-group', () => {
      cy.fillFormInput('name', userGroupName);
      cy.fillFormInput('description', 'Test User Group');
    });

    cy.screenshot('Doc_Admin_User_Groups_Add');

    cy.inMainNavFlyin('add-user-group', () => {
      cy.get('button[data-cy="Save"]').should('be.enabled').click();
    });

    cy.inMainNavContent(() => {
      cy.wait('@createUserGroup');
      cy.waitUntilContentLoaded();
    });

    cy.screenshot('Doc_Admin_User_Groups');

    cy.inMainNavContent(() => {
      cy.contains('tr', userGroupName)
        .should('exist')
        .within(() => {
          cy.contains('mat-icon', 'check_box_outline_blank');
        })
        .click();
    });

    // add ADMIN permission to created user group
    cy.inMainNavFlyin('app-user-group-admin-detail', () => {
      cy.get('button[data-cy^="Assign Permission"]').click();
    });

    cy.inMainNavFlyin('assign-user-group-permission', () => {
      cy.fillFormSelect('permission', 'ADMIN');
    });

    cy.screenshot('Doc_Admin_User_Groups_Permissions_Add');

    cy.inMainNavFlyin('assign-user-group-permission', () => {
      cy.get('button[data-cy="Save"]').should('be.enabled').click();
    });

    // check ADMIN perm.
    cy.waitUntilContentLoaded();
    cy.inMainNavContent(() => {
      cy.contains('tr', userGroupName)
        .should('exist')
        .within(() => {
          cy.contains('ADMIN').should('exist');
        })
        .click();
    });

    logout(cy);
  });

  it('Validates test user cannot access admin UI', () => {
    login(cy, userName, password);

    cy.visit('/');
    cy.get('.local-hamburger-button').click();
    cy.get('button[data-cy=Administration]').should('be.disabled');

    logout(cy);
  });

  it('Adds test user to user group with global ADMIN right', () => {
    cy.login();
    cy.visit('/');
    cy.get('.local-hamburger-button').click();
    cy.get('button[data-cy=Administration]').click();
    cy.contains('a', 'User Groups').click();
    cy.inMainNavContent(() => {
      cy.waitUntilContentLoaded();
      cy.contains('tr', userGroupName)
        .should('exist')
        .within(() => {
          cy.contains('mat-icon', 'check_box_outline_blank');
        })
        .click();
    });
    cy.inMainNavFlyin('app-user-group-admin-detail', () => {
      cy.fillFormInput('addUserInput', userName);
    });

    cy.screenshot('Doc_Admin_User_Groups_Add_Test_User');

    cy.inMainNavFlyin('app-user-group-admin-detail', () => {
      cy.get('mat-icon[name="addUserButton"]').click();
      cy.waitUntilContentLoaded();
    });

    cy.screenshot('Doc_Admin_User_Groups_Test_User_Added_To_Group');

    logout(cy);
  });

  it('Validates test user can access Admin UI and removes itself from user group', () => {
    login(cy, userName, password);

    cy.visit('/');
    cy.get('.local-hamburger-button').click();
    cy.get('button[data-cy=Administration]').should('be.enabled').click();

    // Delete test user from user group
    cy.contains('a', 'User Groups').click();
    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.contains('tr', userGroupName)
        .should('exist')
        .within(() => {
          cy.contains('mat-icon', 'check_box_outline_blank');
        })
        .click();
    });

    cy.inMainNavFlyin('app-user-group-admin-detail', () => {
      cy.contains('tr', userName)
        .should('exist')
        .within(() => {
          cy.contains('mat-icon', 'delete').click();
        });
    });
    cy.waitUntilContentLoaded();

    logout(cy);
  });

  it('Validates test user cannot access admin UI again', () => {
    login(cy, userName, password);

    cy.visit('/');
    cy.get('.local-hamburger-button').click();
    cy.get('button[data-cy=Administration]').should('be.disabled');

    logout(cy);
  });

  it('Clean up', () => {
    cy.login();

    cy.visit('/');
    cy.get('.local-hamburger-button').click();
    cy.get('button[data-cy=Administration]').click();

    // Delete user
    cy.contains('a', 'User Accounts').click();
    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.contains('tr', userName)
        .should('exist')
        .within(() => {
          cy.contains('mat-icon', 'check_box_outline_blank');
        })
        .click();
    });

    cy.inMainNavFlyin('app-user-admin-detail', () => {
      cy.get('button[data-cy^="Delete"]').click();
      cy.contains('app-bd-notification-card', 'Delete User').within(() => {
        cy.get('button[data-cy^=Yes]').click();
      });
    });

    // Delete user group
    cy.contains('a', 'User Groups').click();
    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.contains('tr', userGroupName)
        .should('exist')
        .within(() => {
          cy.contains('mat-icon', 'check_box_outline_blank');
        })
        .click();
    });

    cy.inMainNavFlyin('app-user-group-admin-detail', () => {
      cy.get('button[data-cy^="Delete Group"]').click();
      cy.contains('app-bd-notification-card', 'Delete User Group').within(
        () => {
          cy.get('button[data-cy^=Yes]').click();
        }
      );
    });
  });
});
