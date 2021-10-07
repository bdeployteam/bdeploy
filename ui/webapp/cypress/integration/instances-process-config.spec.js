//@ts-check

describe('Instance Process Config Tests', () => {
  var groupName = 'Demo';
  var instanceName = 'TestInstance';

  before(() => {
    cy.cleanAllGroups();
  });

  beforeEach(() => {
    cy.login();
  });

  it('Prepares the test (group, products, instance)', () => {
    cy.visit('/');
    cy.createGroup(groupName);
    cy.uploadProductIntoGroup(groupName, 'test-product-1-direct.zip');
    cy.uploadProductIntoGroup(groupName, 'test-product-2-direct.zip');
    cy.createInstance(groupName, instanceName, 'Demo Product', '1.0.0');
  });

  it('Configures Processes', () => {
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Instance Configuration');

    cy.waitUntilContentLoaded();

    // Add Server Process
    cy.inMainNavContent(() => {
      cy.get('app-configuration').should('exist');
      cy.contains('mat-toolbar', `Configuration - ${instanceName}`).should('exist');
      cy.get('app-config-node[data-cy="master"]').within((node) => {
        cy.get('button[data-cy^="Add Application"]').click();
      });
    });

    cy.inMainNavFlyin('app-add-process', () => {
      cy.contains('tr', 'Server Application').find('button[data-cy^="Add Server"]').click();
      cy.pressToolbarButton('Close');
    });

    cy.checkMainNavFlyinClosed();

    // Add Client Process
    cy.inMainNavContent(() => {
      cy.get('app-config-node[data-cy="__ClientApplications"]').within((node) => {
        cy.get('button[data-cy^="Add Application"]').click();
      });
    });

    cy.waitUntilContentLoaded();

    cy.inMainNavFlyin('app-add-process', () => {
      cy.contains('tr', 'Client Application').find('button[data-cy^="Add Client"]').click();
      cy.pressToolbarButton('Close');
    });

    cy.checkMainNavFlyinClosed();

    // Check all processes are present
    cy.inMainNavContent(() => {
      cy.get('app-config-node[data-cy="master"]').within((node) => {
        cy.contains('tr', 'Server Application').find('.bd-status-border-added').should('exist');
      });
      cy.get('app-config-node[data-cy="__ClientApplications"]').within((node) => {
        cy.get('.bd-status-border-added').should('have.length', 2);
      });

      cy.pressToolbarButton('Local Changes');
    });

    // Check if local changes have been recorded
    cy.inMainNavFlyin('app-local-changes', () => {
      cy.contains('tr', 'Add Server Application').should('exist');
      cy.contains('tr', 'Add Client Application').contains('mat-icon', 'arrow_back').should('exist');

      cy.get('button[data-cy^="Compare"]').click();
    });

    // Check diff view
    cy.inMainNavFlyin('app-local-diff', () => {
      cy.contains('app-history-diff-field', 'Server Application').find('.local-added-bg').should('exist');
      cy.contains('app-history-diff-field', 'Client Application').find('.local-added-bg').should('exist');

      cy.pressToolbarButton('Back to Overview');
    });

    // Check undoing a change
    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Undo');

      cy.get('app-config-node[data-cy="__ClientApplications"]').within((node) => {
        cy.contains('tr', 'Client Application').should('not.exist');
      });
    });

    // Check if local changes are updated on undo
    cy.inMainNavFlyin('app-local-changes', () => {
      cy.contains('tr', 'Add Server Application').contains('mat-icon', 'arrow_back').should('exist');
      cy.contains('tr', 'Add Client Application').contains('mat-icon', 'redo').should('exist');
    });

    // Check redoing a change
    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Redo');

      cy.get('app-config-node[data-cy="__ClientApplications"]').within((node) => {
        cy.contains('tr', 'Client Application').should('exist');
      });
    });

    // check duplicate name validation when adding another server application.
    cy.inMainNavContent(() => {
      cy.get('app-config-node[data-cy="master"]').within((node) => {
        cy.get('button[data-cy^="Add Application"]').click();
      });
    });

    cy.inMainNavFlyin('app-add-process', () => {
      cy.contains('tr', 'Server Application').find('button[data-cy^="Add Server"]').click();
      cy.pressToolbarButton('Close');
    });

    cy.inMainNavContent(() => {
      cy.get('app-bd-notification-card[header^="Validation"]')
        .should('exist')
        .within(() => {
          cy.contains('tr', 'is not unique').should('exist');
        });

      cy.get('app-config-node[data-cy="master"]').within((node) => {
        cy.contains('tr', 'Server Application').find('.bd-status-border-invalid').should('exist');
      });
    });

    // check configuration of processes
    cy.inMainNavContent(() => {
      cy.get('app-config-node[data-cy="master"]').within((node) => {
        cy.contains('tr', 'Server Application').click();
      });
    });

    cy.inMainNavFlyin('app-edit-process-overview', () => {
      cy.get('button[data-cy^="Configure Parameters"]').click();
    });

    cy.inMainNavFlyin('app-configure-process', () => {
      cy.get('app-bd-form-input[name="name"]').within(() => {
        cy.get('input').should('have.value', 'Server Application');
        cy.get('mat-error').contains('Process name already in use').should('exist');
        cy.get('input').type(' A'); // better name :)
        cy.get('mat-error').should('not.exist');
      });

      // add optional sleep parameter and set value
      cy.contains('mat-expansion-panel', 'Sleep Configuration').within(() => {
        cy.get('mat-panel-title').click();
        cy.get('mat-expansion-panel-header').should('have.attr', 'aria-expanded', 'true');

        cy.get('button[data-cy^="Select Parameters"]').click();

        cy.get('[data-cy="param.sleep"]').within(() => {
          cy.get('input').should('be.disabled');
          cy.contains('mat-icon', 'add').click();

          cy.contains('mat-icon', 'delete').should('exist');
        });

        cy.get('button[data-cy^="Confirm"]').click();

        cy.get('[data-cy="param.sleep"]').within(() => {
          cy.get('input').should('be.enabled').should('have.value', 10);
          cy.get('input').clear().type('5');
        });
      });

      // check preview.
      cy.contains('mat-expansion-panel', 'Command Line Preview').within(() => {
        cy.get('mat-panel-title').click();
        cy.contains('app-history-diff-field', '--sleep=5').should('exist');
      });

      cy.get('button[data-cy="APPLY"]').click();
    });

    cy.inMainNavContent(() => {
      cy.get('button[data-cy^="Save"]').should('be.enabled').click(); // disables button on click.
      cy.waitUntilContentLoaded();

      // "added" border should be gone, we're in sync now.
      cy.get('app-config-node[data-cy="master"]').within((node) => {
        cy.contains('tr', 'Server Application').find('.bd-status-border-none').should('exist');
      });
    });

    // Add another Client Process, check discarding changes
    cy.inMainNavContent(() => {
      cy.get('app-config-node[data-cy="__ClientApplications"]').within((node) => {
        cy.get('button[data-cy^="Add Application"]').click();
      });
    });

    cy.inMainNavFlyin('app-add-process', () => {
      cy.contains('tr', 'Client Application').find('button[data-cy^="Add Client"]').click();
    });

    cy.inMainNavContent(() => {
      cy.get('button[data-cy^="Save"]').should('be.enabled');
      cy.pressToolbarButton('Local Changes');
    });

    // Check if local changes have been recorded
    cy.inMainNavFlyin('app-local-changes', () => {
      cy.contains('tr', 'Add Client Application').contains('mat-icon', 'arrow_back').should('exist');

      cy.get('button[data-cy^="Discard"]').click();

      cy.get('app-bd-dialog-message').within(() => {
        cy.contains('Discard unsaved changes').should('exist');
        cy.get('button[data-cy="YES"]').click();
      });

      cy.get('button[data-cy^="Compare"]').should('be.disabled');
      cy.get('button[data-cy^="Discard"]').should('be.disabled');
    });

    cy.inMainNavContent(() => {
      cy.get('button[data-cy^="Save"]').should('be.disabled');
    });
  });

  // this test is in here since we have some processes configured already.
  it('Tests product update', () => {
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Instance Configuration');

    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Instance Settings');
    });

    cy.inMainNavFlyin('app-instance-settings', () => {
      cy.get('button[data-cy^="Update Product"]').click();
    });

    cy.inMainNavFlyin('app-product-update', () => {
      cy.contains('tr', '2.0.0').within(() => {
        cy.get('button[data-cy^="Use"]').click();
      });
    });

    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Instance Settings');
    });

    cy.checkMainNavFlyinClosed();

    cy.contains('app-bd-notification-card', 'Product Update')
      .should('exist')
      .within(() => {
        cy.contains('tr', 'updated configuration files')
          .should('exist')
          .within(() => {
            cy.get('button[data-cy^="Dismiss"]').click();
          });
      });

    cy.contains('app-bd-notification-card', 'Product Update').should('not.exist');
    cy.pressToolbarButton('Undo');

    cy.contains('app-bd-notification-card', 'Product Update')
      .should('exist')
      .within(() => {
        cy.get('[data-cy="notification-dismiss"]').click();
      });

    cy.contains('app-bd-notification-card', 'Product Update').should('not.exist');

    cy.waitUntilContentLoaded(); // validation may be running.
    cy.get('app-bd-notification-card[header^="Validation"]').should('not.exist');

    cy.get('app-config-node[data-cy="master"]').within((node) => {
      cy.contains('tr', 'Server Application A').find('.bd-status-border-changed').should('exist');
    });

    cy.get('button[data-cy^="Save"]').should('be.enabled').click(); // disables button on click.
    cy.waitUntilContentLoaded();

    // "changed" border should be gone, we're in sync now.
    cy.get('app-config-node[data-cy="master"]').within((node) => {
      cy.contains('tr', 'Server Application A').find('.bd-status-border-none').should('exist');
    });
  });

  it('Cleans up', () => {
    cy.deleteGroup(groupName);
  });
});
