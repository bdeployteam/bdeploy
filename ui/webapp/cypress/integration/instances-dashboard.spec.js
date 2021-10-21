//@ts-check

describe('Instance Dashboard Tests', () => {
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

  it('Prepares Instance Version', () => {
    cy.enterInstance(groupName, instanceName);
    cy.screenshot('Doc_InstanceEmpty');

    cy.pressMainNavButton('Instance Configuration');

    cy.waitUntilContentLoaded();

    // create some from a template
    cy.inMainNavContent(() => {
      cy.contains('.bd-rect-card', 'The instance is currently empty').within(() => {
        cy.get('button[data-cy^="Apply Instance Template"]').click();
      });
    });

    cy.waitUntilContentLoaded();

    cy.inMainNavFlyin('app-instance-templates', () => {
      cy.contains('tr', 'Default Configuration')
        .should('exist')
        .within(() => {
          cy.get('button').click();
        });

      cy.contains('app-bd-notification-card', 'Assign Template').within(() => {
        cy.fillFormSelect('Server Apps', 'Apply to master');
        cy.fillFormSelect('Client Apps', 'Apply to Client Applications');

        cy.get('button[data-cy="Confirm"]').click();
      });

      cy.contains('app-bd-notification-card', 'Assign Variable Values').within(() => {
        cy.fillFormInput('Text Value', 'Test');
        cy.fillFormInput('Sleep Timeout', '5');

        cy.get('button[data-cy="Confirm"]').click();
      });
    });

    cy.inMainNavContent(() => {
      cy.waitForApi(() => {
        cy.pressToolbarButton('Save');
      });

      cy.waitUntilContentLoaded();
    });
  });

  it('Test Dashboard', () => {
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

    cy.contains('app-instance-server-node', 'master')
      .should('exist')
      .within(() => {
        cy.contains('tr', 'Server No Sleep').should('exist');
        cy.contains('tr', 'Server With Sleep').should('exist');
      });

    cy.screenshot('Doc_InstanceDashboardActive');

    cy.get('app-instance-client-node')
      .should('exist')
      .within(() => {
        cy.get('tr:contains("Client Test")').should('have.length', 2);

        cy.contains('tr', 'Client Test').click(); // the first one, no idea which OS.
      });

    cy.inMainNavFlyin('app-client-detail', () => {
      cy.get('app-bd-expand-button[data-cy="Usage Statistics"]')
        .click()
        .within(() => {
          cy.contains('No data to show').should('exist');
        })
        .click('top');

      cy.get('button[data-cy^="Click"]').downloadByLinkClick('dashboard-click-start.json');
    });
  });

  it('Test Manual Confirm', () => {
    cy.inMainNavContent(() => {
      cy.contains('tr', 'Server No Sleep').click();
    });

    cy.inMainNavFlyin('app-process-status', () => {
      cy.contains('button', 'play_arrow').click();
    });

    cy.screenshot('Doc_DashboardProcessManualConfirm');

    cy.inMainNavFlyin('app-process-status', () => {
      cy.contains('app-bd-notification-card', 'Confirm').within(() => {
        cy.get('button[data-cy="Cancel"]').click();
      });
    });
  });

  it('Test Process Control', () => {
    cy.inMainNavContent(() => {
      cy.contains('tr', 'Another Server With Sleep').click();
    });

    cy.inMainNavFlyin('app-process-status', () => {
      cy.get('button[data-cy="Process Port Status"]').click();
    });

    cy.inMainNavFlyin('app-process-ports', () => {
      cy.contains('No server ports').should('exist');
      cy.pressToolbarButton('Back to Overview');
    });

    cy.screenshot('Doc_DashboardProcessControl');

    cy.inMainNavFlyin('app-process-status', () => {
      cy.contains('button', 'play_arrow').click();
      cy.contains('button', 'stop').should('be.enabled');

      // first start
      cy.contains('Up Time').should('exist');
      cy.contains('Started At').should('exist');
      cy.contains('button', 'play_arrow').should('be.disabled');

      // crash back off after second start
      cy.contains('Stopped At').should('exist');
      cy.contains('Restart In').should('exist');
    });

    cy.screenshot('Doc_DashboardProcessCrash');

    cy.inMainNavFlyin('app-process-status', () => {
      // permanent crash
      cy.contains('mat-icon', 'error').should('exist');
      cy.contains('Stopped At').should('exist');
      cy.contains('button', 'stop').should('be.disabled');
      cy.contains('button', 'play_arrow').should('be.enabled');
    });

    cy.screenshot('Doc_DashboardProcessCrashPermanent');

    cy.inMainNavContent(() => {
      cy.contains('tr', 'Another Server With Sleep').within(() => {
        cy.contains('mat-icon', 'error').should('exist');
      });
    });

    cy.inMainNavFlyin('app-process-status', () => {
      cy.get('button[data-cy="Process Console"]').click();
    });

    cy.screenshot('Doc_DashboardProcessConsole');

    cy.inMainNavFlyin('app-process-console', () => {
      cy.pressToolbarButton('Back to Overview');
    });
  });

  it('Tests card mode', () => {
    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Toggle Card Mode');

      cy.contains('app-instance-server-node', 'master').within(() => {
        cy.contains('mat-card', 'Server No Sleep').should('exist');
        cy.contains('mat-card', 'Server With Sleep').should('exist');
      });
    });
  });

  it('Tests collapsed mode', () => {
    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Collapsed Mode');

      cy.contains('app-instance-server-node', 'master').within(() => {
        cy.contains('mat-card', 'Server No Sleep').should('not.exist');
        cy.contains('tr', 'Server No Sleep').should('not.exist');
      });
    });
  });

  it('Tests node details', () => {
    cy.inMainNavContent(() => {
      cy.contains('app-instance-server-node', 'master').within(() => {
        cy.get('button[data-cy="Details"]').click();
      });
    });

    cy.inMainNavFlyin('app-node-details', () => {
      cy.contains('app-bd-notification-card', 'master').should('exist');
      cy.get('app-node-header[show="load"]').should('exist');
      cy.get('app-node-header[show="cpu"]').should('exist');
    });
  });

  it('Cleans up', () => {
    cy.deleteGroup(groupName);
  });
});
