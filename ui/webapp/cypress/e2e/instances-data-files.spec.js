//@ts-check

describe('Instance Data Files Tests', () => {
  var groupName = 'Demo';
  var instanceName = 'TestInstance';

  beforeEach(() => {
    cy.login();
  });

  it('Prepares the test (group, products, instance)', () => {
    cy.visit('/');
    cy.createGroup(groupName);

    cy.visit('/');
    cy.uploadProductIntoGroup(groupName, 'test-product-2-direct.zip');

    cy.visit('/');
    cy.createInstance(groupName, instanceName, 'Demo Product', '2.0.0');
  });

  it('Configures Processes', () => {
    cy.visit('/');
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Instance Configuration');

    // Add Server Process
    cy.inMainNavContent(() => {
      cy.get('app-configuration').should('exist');
      cy.contains('mat-toolbar', instanceName).should('exist');
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

      cy.get('button[data-cy="Apply"]').click();
    });

    cy.inMainNavContent(() => {
      cy.get('button[data-cy^="Save"]').should('be.enabled').click(); // disables button on click.
      cy.waitUntilContentLoaded();
    });

    // save navigates to dashboard, navigate back
    cy.pressMainNavButton('Instance Configuration');

    cy.inMainNavContent(() => {
      // "added" border should be gone, we're in sync now.
      cy.get('app-config-node[data-cy="master"]').within((node) => {
        cy.contains('tr', 'Server Application').find('.bd-status-border-none').should('exist');
      });
    });
  });

  it('Produces data file', () => {
    cy.visit('/');
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Instance Dashboard');

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
    cy.visit('/');
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Data Files');

    cy.waitUntilContentLoaded();
    cy.screenshot('Doc_DataFiles');

    // additionally open preview once.
    cy.inMainNavContent(() => {
      cy.contains('tr', 'out.txt').click();
    });

    cy.waitUntilContentLoaded();
    cy.screenshot('Doc_DataFilesView');

    cy.inMainNavFlyin('app-data-file-viewer', () => {
      cy.pressToolbarButton('Edit');
    });

    cy.inMainNavFlyin('app-data-file-editor', () => {
      cy.monacoEditor().should('contain.value', 'TEST');
    });

    cy.screenshot('Doc_DataFilesEdit');

    cy.inMainNavFlyin('app-data-file-editor', () => {
      cy.pressToolbarButton('Back');
    });

    cy.inMainNavFlyin('app-data-file-viewer', () => {
      cy.pressToolbarButton('Close');
    });
  });

  it('Checks adding and modifying data files', () => {
    cy.visit('/');
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Data Files');

    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Add File');
    });

    cy.inMainNavFlyin('app-add-data-file', () => {
      cy.contains('strong[name="targetNode"]', 'master').should('exist');
      cy.fillFormInput('path', 'test.txt');
      cy.get('button[data-cy="Save"]').should('be.enabled').click();
    });

    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.contains('tr', 'test.txt').should('exist').click();
    });

    cy.inMainNavFlyin('app-data-file-viewer', () => {
      cy.pressToolbarButton('Edit');
    });

    cy.inMainNavFlyin('app-data-file-editor', () => {
      cy.typeInMonacoEditor('This is a test');
      cy.pressToolbarButton('Save');
    });

    cy.waitUntilContentLoaded();
    cy.inMainNavFlyin('app-data-file-viewer', () => {
      cy.pressToolbarButton('Edit');
    });

    cy.inMainNavFlyin('app-data-file-editor', () => {
      cy.monacoEditor().should('have.value', 'This is a test');
      cy.pressToolbarButton('Back');
    });
  });

  it('Tests replacing a file', () => {
    cy.visit('/');
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Data Files');

    cy.inMainNavContent(() => {
      cy.contains('tr', 'test.txt').should('exist').and('not.contain', '0 B');
      cy.pressToolbarButton('Add File');
    });

    cy.inMainNavFlyin('app-add-data-file', () => {
      cy.contains('strong[name="targetNode"]', 'master').should('exist');
      cy.fillFormInput('path', 'test.txt');
      cy.get('button[data-cy="Save"]').should('be.enabled').click();
    });

    cy.contains('app-bd-notification-card', 'File Exists')
      .should('exist')
      .within(() => {
        cy.get('button[data-cy="Yes"]').click();
      });

    cy.waitUntilContentLoaded();

    cy.contains('tr', 'test.txt').should('exist').and('contain', '0 B');
  });

  it('Checks bulk manipulation', () => {
    cy.visit('/');
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Data Files');

    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Bulk Manipulation');
      cy.contains('tr', 'test.txt').within(() => {
        cy.get('input[type="checkbox"]').check({ force: true });
      });
    });

    cy.inMainNavFlyin('app-data-files-bulk-manipulation', () => {
      cy.get('button[data-cy="Delete Selected Data Files"]').should('be.enabled').click();
    });

    cy.contains('app-bd-notification-card', 'Delete 1 data files?').within(() => {
      cy.fillFormInput('confirm', 'I UNDERSTAND');
      cy.get('button[data-cy="Yes"]').should('be.enabled').click();
      cy.contains('tr', 'test.txt').should('not.exist');
    });
  });
});
