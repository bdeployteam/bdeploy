//@ts-check

describe('Instance log files tests', () => {
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

  it('Configures processes', () => {
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

        cy.get('[data-cy="param.out"]').within(() => {
          cy.fillFormInput('param.out_link', '{{P:LOG_DATA}}/out.txt');
        });
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

  it('Produces log file', () => {
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

  it('Checks log file from process', () => {
    cy.visit('/');
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Log Files');

    cy.waitUntilContentLoaded();
    cy.screenshot('Doc_LogFiles');

    // additionally open preview once.
    cy.inMainNavContent(() => {
      cy.contains('tr', 'out.txt').click();
    });

    cy.waitUntilContentLoaded();
    cy.screenshot('Doc_LogFilesView');

    cy.inMainNavFlyin('app-file-viewer', () => {
      cy.pressToolbarButton('Close');
    });
  });

  it('Checks bulk manipulation', () => {
    cy.visit('/');
    cy.enterInstance(groupName, instanceName);
    cy.pressMainNavButton('Log Files');

    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Bulk Manipulation');
      cy.contains('tr', 'out.txt').within(() => {
        cy.get('input[type="checkbox"]').check({ force: true });
      });
    });
  });
});
