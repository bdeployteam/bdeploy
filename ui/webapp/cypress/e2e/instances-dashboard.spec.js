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
      cy.contains('.bd-rect-card', 'The instance is currently empty').within(
        () => {
          cy.get('button[data-cy^="Apply Instance Template"]').click();
        }
      );
    });

    cy.waitUntilContentLoaded();

    cy.inMainNavFlyin('app-instance-templates', () => {
      cy.fillFormSelect('Template', 'Default Configuration');

      cy.fillFormSelect('Server Apps', 'Apply to master');
      cy.fillFormSelect('Client Apps', 'Apply to Client Applications');
      cy.get('button[data-cy="Next"]').click();

      cy.fillFormInput('Text Value', 'Test');
      cy.fillFormInput('Sleep Timeout', '10');
      cy.get('button[data-cy="Confirm"]').click();
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

      cy.get('button[data-cy^="Click"]').downloadByLinkClick(
        'dashboard-click-start.json'
      );
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
    cy.enterInstance(groupName, instanceName);
    cy.waitUntilContentLoaded();

    cy.checkMainNavFlyinClosed();

    cy.inMainNavContent(() => {
      cy.contains('tr', 'Another Server With Sleep').click();
    });

    cy.waitUntilContentLoaded();

    cy.inMainNavFlyin('app-process-status', () => {
      cy.contains('Another Server').should('exist');
      cy.contains('button', 'play_arrow').should('be.enabled');
      cy.get('button[data-cy="Process Port Status"]').click();
    });

    cy.inMainNavFlyin('app-process-ports', () => {
      cy.contains('No server ports').should('exist');
      cy.pressToolbarButton('Back to Overview');
    });

    cy.screenshot('Doc_DashboardProcessControl');

    cy.inMainNavFlyin('app-process-status', () => {
      cy.contains('button', 'play_arrow').click();

      // first start
      cy.contains('Up Time').should('exist');
      cy.contains('Started At').should('exist');
      cy.contains('button', 'play_arrow').should('be.disabled');

      cy.waitUntilContentLoaded();

      // crash back off after second start
      cy.contains('Restart In').should('exist');
    });

    cy.screenshot('Doc_DashboardProcessCrash');

    cy.inMainNavFlyin('app-process-status', () => {
      // wait for restart to actually happen
      cy.contains('Restart In').should('not.exist');
      cy.contains('Started At').should('exist');

      // wait for process to stop
      cy.contains('Stopped At').should('exist');

      // permanent crash
      cy.contains('mat-icon', 'error').should('exist');
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

  it('Test Selected Bulk Process Control', () => {
    cy.enterInstance(groupName, instanceName);
    cy.waitUntilContentLoaded();

    cy.checkMainNavFlyinClosed();

    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Bulk Control');
    });

    cy.waitUntilContentLoaded();

    cy.inMainNavFlyin('app-bulk-control', () => {
      cy.contains('div', 'selected processes.')
        .should('exist')
        .within(() => {
          cy.contains('strong', '0').should('exist');
        });
    });

    cy.inMainNavContent(() => {
      cy.contains('app-instance-server-node', 'master').within(() => {
        cy.contains('tr', 'Server With Sleep').within(() => {
          cy.get('input[type="checkbox"]').check({ force: true });
        });
        cy.contains('tr', 'Another Server With Sleep').within(() => {
          cy.get('input[type="checkbox"]').check({ force: true });
        });
      });
    });

    cy.screenshot('Doc_DashboardBulkProcessControl');
    cy.intercept({
      method: 'GET',
      url: '**/api/group/**/instance/**/processes',
    }).as('list');

    cy.inMainNavFlyin('app-bulk-control', () => {
      cy.contains('div', 'selected processes.')
        .should('exist')
        .within(() => {
          cy.contains('strong', '2').should('exist');
        });

      cy.get('button[data-cy="Start Selected Processes"]').click();

      cy.contains('app-bd-notification-card', 'Confirm Start').within(() => {
        cy.get('button[data-cy="Yes"]').click();
      });
    });

    cy.waitUntilContentLoaded();
    cy.wait('@list');
    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.get('tr:contains("OK")').should('have.length', 2);
    });

    cy.inMainNavFlyin('app-bulk-control', () => {
      cy.contains('div', 'selected processes.')
        .should('exist')
        .within(() => {
          cy.contains('strong', '2').should('exist');
        });

      cy.get('button[data-cy="Stop Selected Processes"]').click();

      cy.contains('app-bd-notification-card', 'Confirm Stop').within(() => {
        cy.get('button[data-cy="Yes"]').click();
      });
    });

    cy.waitUntilContentLoaded();
    cy.wait('@list');
    cy.waitUntilContentLoaded();

    cy.inMainNavContent(() => {
      cy.get('tr:contains("stop")').should('have.length', 3);
    });
  });

  it('Tests card mode', () => {
    cy.inMainNavContent(() => {
      cy.pressToolbarButton('Toggle Card Mode');

      cy.contains('app-instance-server-node', 'master').within(() => {
        cy.contains('mat-card', 'Server No Sleep').should('exist');
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
