//@ts-check

describe('Instance Data Files Tests', () => {
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
    cy.uploadProductIntoGroup(groupName, 'test-product-2-direct.zip');
    cy.createInstance(groupName, instanceName, 'Demo Product', '2.0.0');
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
      // add optional output parameter with default value
      cy.contains('mat-expansion-panel', 'Test Parameters').within(() => {
        cy.get('mat-panel-title').click();
        cy.get('mat-expansion-panel-header').should('have.attr', 'aria-expanded', 'true');

        cy.get('button[data-cy^="Select Parameters"]').click();

        cy.get('[data-cy="param.out"]').within(() => {
          cy.contains('mat-icon', 'add').click();
          cy.contains('mat-icon', 'delete').should('exist');
        });

        cy.get('button[data-cy^="Confirm"]').click();
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
  });

  it('Produces data file', () => {
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Instance Dashboard');

    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.contains('.bd-rect-card', 'has no active version')
        .should('exist')
        .within(() => {
          cy.waitForApi(() => {
            cy.get('button[data-cy="Install"]').should('be.enabled').click();
          });

          cy.waitForApi(() => {
            cy.get('button[data-cy="Activate"]').should('be.enabled').click();
          });
        });
    });

    cy.inMainNavContent(() => {
      cy.contains('tr', 'Server Application').click();
    });

    cy.inMainNavFlyin('app-process-status', () => {
      cy.contains('button', 'play_arrow').click();

      cy.contains('Stopped At').should('exist');
      cy.contains('button', 'stop').should('be.enabled').click();
    });
  });

  it('Checks data file from process', () => {
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Data Files');

    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.contains('tr', 'out.txt').within(() => {
        cy.get('button[data-cy="Edit"]').click();
      });
    });

    cy.waitUntilContentLoaded();

    cy.inMainNavFlyin('app-data-file-editor', () => {
      cy.monacoEditor().should('contain.value', 'TEST');
      cy.pressToolbarButton('Close');
    });

    // additionally open preview once.
    cy.inMainNavContent(() => {
      cy.contains('tr', 'out.txt').click();
    });

    cy.waitUntilContentLoaded();

    cy.inMainNavFlyin('app-data-file-viewer', () => {
      cy.pressToolbarButton('Close');
    });
  });

  it('Checks adding and modifying data files', () => {
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Data Files');

    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Add File');
      cy.contains('app-bd-notification-card', 'Add Data File').within(() => {
        cy.fillFormSelect('minion', 'master');
        cy.fillFormInput('path', 'test.txt');
        cy.get('button[data-cy="OK"]').should('be.enabled').click();
      });

      cy.waitUntilContentLoaded();

      cy.contains('tr', 'test.txt')
        .should('exist')
        .within(() => {
          cy.get('button[data-cy="Edit"]').click();
        });
    });

    cy.waitUntilContentLoaded();

    cy.inMainNavFlyin('app-data-file-editor', () => {
      cy.typeInMonacoEditor('This is a test');
      cy.pressToolbarButton('APPLY');
    });

    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.contains('tr', 'test.txt')
        .should('exist')
        .within(() => {
          cy.get('button[data-cy="Edit"]').click();
        });
    });

    cy.inMainNavFlyin('app-data-file-editor', () => {
      cy.monacoEditor().should('have.value', 'This is a test');
      cy.pressToolbarButton('Close');
    });
  });

  it('Tests replacing a file', () => {
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Data Files');

    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.contains('tr', 'test.txt')
        .should('exist')
        .within(() => {
          cy.contains('0 B').should('not.exist');
        });
      cy.pressToolbarButton('Add File');

      cy.contains('app-bd-notification-card', 'Add Data File').within(() => {
        cy.fillFormSelect('minion', 'master');
        cy.fillFormInput('path', 'test.txt');
        cy.get('button[data-cy="OK"]').should('be.enabled').click();
      });

      cy.contains('app-bd-notification-card', 'File Exists')
        .should('exist')
        .within(() => {
          cy.get('button[data-cy="YES"]').click();
        });

      cy.waitUntilContentLoaded();

      cy.contains('tr', 'test.txt')
        .should('exist')
        .within(() => {
          cy.contains('0 B').should('exist');
        });
    });
  });

  it('Cleans up', () => {
    cy.deleteGroup(groupName);
  });
});
