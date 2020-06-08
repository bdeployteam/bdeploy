describe("Central/Managed Basic Test", function() {
  var groupName = "Demo";
  var managedInstance;
  var centralInstance;

  it("creates instance group on central", () => {
    cy.createInstanceGroup(groupName, "CENTRAL");
  });

  it("attach managed server to central", () => {
    cy.attachManaged(groupName, true);
  });

  it("deletes and re-attaches instance group on managed server", () => {
    cy.deleteInstanceGroup(groupName, "MANAGED");

    cy.visitBDeploy("/", "CENTRAL");

    // instance group is still there on central.
    cy.get('[data-cy="group-' + groupName + '"]')
      .first()
      .should("exist");

    cy.visitBDeploy("/#/servers/browser/" + groupName, "CENTRAL");
    cy.contains("mat-expansion-panel", "Managed Server")
      .should("exist")
      .and("be.visible")
      .within(e => {
        cy.contains("button", "Synchronize").click();
        cy.get("mat-spinner").should("not.exist");
      });

    cy.contains("mat-dialog-container", "Synchronization Error")
      .should("exist")
      .within(dialog => {
        cy.contains("may no longer exist").should("exist");
        cy.contains("button", "OK").click();
      });

    cy.contains("mat-expansion-panel", "Managed Server")
      .should("exist")
      .and("be.visible")
      .within(e => {
        cy.contains("button", "Delete").click();
      });

    cy.contains("mat-dialog-container", "Delete Managed Server")
      .should("exist")
      .within(dialog => {
        cy.contains("button", "OK").click();
      });

    cy.attachManaged(groupName); // re-attach
  });

  it("creates instance on managed", () => {
    cy.uploadProductIntoGroup(groupName, 'test-product-1-direct.zip', 'MANAGED');
    cy.createInstance(groupName, 'ManagedInstance', 'MANAGED', '1.0.0').then(id => {
      managedInstance = id;
    });

    cy.visitBDeploy('/#/instance/browser/' + groupName, 'MANAGED');
    cy.contains('app-instance-card', 'ManagedInstance').should('exist');

    cy.visitBDeploy('/#/instance/browser/' + groupName, 'CENTRAL');
    cy.contains('app-instance-card', 'ManagedInstance').should('not.exist');

    cy.visitBDeploy('/#/servers/browser/' + groupName, 'CENTRAL');
    cy.contains("mat-expansion-panel", "Managed Server")
      .should("exist")
      .and("be.visible")
      .within(e => {
        cy.contains("button", "Synchronize").click();
        cy.get("mat-spinner").should("not.exist");
      });

    cy.visitBDeploy('/#/instance/browser/' + groupName, 'CENTRAL');
    cy.contains('app-instance-card', 'ManagedInstance').should('exist').within(i => {
      cy.contains('N/A').should('exist');
    });
  });

  it("synchronizes product version 1.0.0 from managed to central", () => {
    cy.visitBDeploy('/#/instancegroup/products/' + groupName, 'CENTRAL');
    cy.waitUntilContentLoaded();
    cy.screenshot('BDeploy_Product_Sync_Button');
    cy.contains('button', 'sync_alt').click();

    cy.waitUntilContentLoaded();
    cy.contains('mat-label', 'Source').should('be.visible').click({force: true});
    cy.get('mat-option').contains('Managed Server').click();

    // wait for the drop down to go away...
    cy.get('mat-option').should('not.exist');

    cy.contains('mat-label', 'Target').click({force: true});
    cy.get('mat-option').contains('Central').click();

    cy.waitUntilContentLoaded();
    cy.screenshot('BDeploy_Product_Sync_Wizard');

    cy.contains('button', 'Next').click();

    cy.contains('mat-label', 'Product').click({force: true});
    cy.get('mat-option').contains('Demo').click();

    cy.waitUntilContentLoaded();
    cy.screenshot('BDeploy_Product_Sync_Version');

    cy.get('[data-cy="prod-source"]').within(s => {
      cy.contains('div', '1.0.0').siblings('mat-icon').contains('arrow_forward').click();
    });

    cy.get('[data-cy="prod-target"]').within(t => {
      cy.contains('div', '1.0.0').should('exist');
    });

    // need to make sure we hit the correct next button - the one from the first step is still in the DOM!
    cy.get('[data-cy="prod-target"]').parent().parent().within(p => {
      cy.contains('button', 'Next').click();
    })

    // Wait until the transfer is done
    cy.contains("Product transfer successfully done.");

    cy.screenshot('BDeploy_Product_Sync_Done');

    cy.contains('button', 'Done').click();

    cy.contains('mat-card', 'Demo Product').click();
    cy.get('app-product-list').within(pl => {
      cy.contains('mat-list-item', '1.0.0').should('exist');
    });

    cy.visitBDeploy('/#/instance/browser/' + groupName, 'CENTRAL');
    cy.contains('app-instance-card', 'ManagedInstance').should('exist').within(i => {
      cy.contains('N/A').should('not.exist');
    });
  });

  it("creates instance on central", () => {
    cy.uploadProductIntoGroup(groupName, 'test-product-2-direct.zip', 'CENTRAL');
    cy.createInstance(groupName, 'CentralInstance', 'CENTRAL', '2.0.0', 'Managed Server').then(id => {
      centralInstance = id;
    });

    cy.visitBDeploy('/#/instance/browser/' + groupName, 'MANAGED');
    cy.contains('app-instance-card', 'CentralInstance').should('exist');

    cy.visitBDeploy('/#/instance/browser/' + groupName, 'CENTRAL');
    cy.contains('app-instance-card', 'CentralInstance').should('exist');

    cy.waitUntilContentLoaded();
    cy.screenshot('BDeploy_Central_Instance_With_Sync');
  });

  it("configures instance on managed and sync to central", () => {
    cy.gotoInstance(groupName, managedInstance, 'MANAGED');

    cy.get('app-instance-group-logo').parent().clickContextMenuAction('Configure Applications');

    cy.getNodeCard('master').contains('Drop server application here').should('be.visible').then(el => {
      cy.contains('app-application-descriptor-card', 'Server Application').dragTo(el);
    });

    cy.getNodeCard('Client Applications').contains('Drop client application here').should('be.visible').then(el => {
      cy.contains('app-application-descriptor-card', 'Client Application').dragTo(el);
    });

    // wait for the application init to be done. a normal user will likely never see this :)
    cy.getApplicationConfigCard('master', 'Server Application').should('exist')
    cy.getApplicationConfigCard('master', 'Server Application').contains('Initializing...').should('not.exist')

    cy.getApplicationConfigCard('Client Applications', 'Client Application').should('exist')
    cy.getApplicationConfigCard('Client Applications', 'Client Application').contains('Initializing...').should('not.exist')

    cy.contains('button', 'SAVE').click();
    cy.waitUntilContentLoaded();

    // create a config file
    cy.get('app-instance-group-logo').parent().clickContextMenuAction('Configuration Files');
    cy.contains('button', 'add').click();
    cy.get('input[placeholder="Enter path for file"]').clear().type('cypress.cfg')
    cy.typeInAceEditor('CY-CFG');
    cy.contains('button', 'APPLY').click();
    cy.contains('td', 'cypress.cfg').should('exist');
    cy.contains('button', 'SAVE').click();
    cy.waitUntilContentLoaded();

    // make sure that the instance version is NOT yet visible on the central
    cy.gotoInstance(groupName, managedInstance, 'CENTRAL');

    cy.contains('No applications have been configured yet').should('exist')
    // cannot check configuration file here, as it would require a sync :)

    // now sync to central and make sure it appeared
    cy.contains('mat-icon', 'dns').click();

    // the next actions are not retryable, so sync must be completed before checking
    cy.waitUntilContentLoaded();
    cy.contains('There are currently no server applications').should('not.exist');

    cy.getApplicationConfigCard('master', 'Server Application').should('exist')
    cy.getApplicationConfigCard('Client Applications', 'Client Application').should('exist')

    cy.get('app-instance-group-logo').parent().clickContextMenuAction('Configuration Files');
    cy.contains('td', 'cypress.cfg').should('exist');
  });

  it("installs and activates on the central server", () => {
    cy.gotoInstance(groupName, managedInstance, 'CENTRAL');
    cy.waitUntilContentLoaded();

    cy.contains('mat-icon', 'dns').click();
    cy.waitUntilContentLoaded();

    cy.getLatestInstanceVersion().installAndActivate();

    cy.gotoInstance(groupName, managedInstance, 'MANAGED');
    cy.waitUntilContentLoaded();

    cy.getActiveInstanceVersion().should('exist');
  })

  it("checks if product version is synced to managed", () => {
    cy.gotoInstance(groupName, managedInstance, 'CENTRAL');
    cy.waitUntilContentLoaded();

    cy.contains('mat-icon', 'dns').click();
    cy.waitUntilContentLoaded();

    cy.get('.notifications-button').click();
    cy.contains('button', 'Show Product Versions').click();

    cy.contains('mat-toolbar', 'Change Product Version').should('exist');
    cy.contains('app-product-tag-card', '2.0.0').should('exist').contains('button', 'arrow_upward').click();
    cy.waitUntilContentLoaded();

    cy.getApplicationConfigCard('master', 'Server Application').clickContextMenuAction('Configure')

    // set sleep parameter
    cy.addAndSetOptionalParameter('Sleep Configuration', 'Sleep Timeout', '120');
    cy.contains('button', 'APPLY').click();

    cy.contains('button', 'SAVE').click();
    cy.waitUntilContentLoaded();

    cy.getLatestInstanceVersion().installAndActivate();

    cy.visitBDeploy('/#/instance/browser/' + groupName, 'MANAGED');
    cy.waitUntilContentLoaded();
    cy.get('[data-cy=instance-' + managedInstance + ']').should('exist').within(ic => {
      cy.contains('2.0.0').should('exist');
      cy.contains('N/A').should('not.exist');
    });
  })

  it("controls process from central and managed", () => {
    cy.gotoInstance(groupName, managedInstance, 'CENTRAL');
    cy.waitUntilContentLoaded();

    cy.contains('mat-icon', 'dns').click();
    cy.waitUntilContentLoaded();

    cy.startProcess('master', 'Server Application');

    // click process output button
    cy.contains('app-process-details', 'Server Application').within(() => {
      cy.contains('button', 'message').click();
    })

    // check process output and close overlay
    cy.get('app-file-viewer').within(() => {
      cy.contains('button', 'close').click();
    })

    // check that process is marked as running
    cy.getApplicationConfigCard('master', 'Server Application').within(() => {
      cy.get('app-process-status').find('.app-process-running').should('exist')
    })

    // check that process is marked running on managed server as well.
    cy.gotoInstance(groupName, managedInstance, 'MANAGED');
    cy.waitUntilContentLoaded();

    cy.getApplicationConfigCard('master', 'Server Application').click();
    cy.getApplicationConfigCard('master', 'Server Application').within(() => {
      cy.get('app-process-status').find('.app-process-running').should('exist')
    })

    // click process output button
    cy.contains('app-process-details', 'Server Application').within(() => {
      cy.contains('button', 'message').click();
    })

    // check process output and close overlay
    cy.get('app-file-viewer').within(() => {
      cy.contains('button', 'close').click();
    })

    // stop and check that the process is marked as stopped
    cy.contains('app-process-details', 'Server Application').within(() => {
      cy.contains('button', 'stop').click();
      cy.get('app-process-status').find('.app-process-stopped').should('exist')
    })

    // check whether process is stopped on central as well.
    cy.gotoInstance(groupName, managedInstance, 'CENTRAL');
    cy.waitUntilContentLoaded();

    cy.contains('mat-icon', 'dns').click();
    cy.waitUntilContentLoaded();

    cy.getApplicationConfigCard('master', 'Server Application').click();
    cy.getApplicationConfigCard('master', 'Server Application').within(() => {
      cy.get('app-process-status').find('.app-process-running').should('not.exist')
    })

    cy.contains('app-process-details', 'Server Application').within(() => {
      cy.get('app-process-status').find('.app-process-stopped').should('exist')
    })
  })

  it("deletes instance group on central server", () => {
    cy.deleteInstanceGroup(groupName, "CENTRAL");

    cy.visitBDeploy("/", "MANAGED");

    // instance group is still there on managed.
    cy.get('[data-cy="group-' + groupName + '"]')
      .first()
      .should("exist");

    cy.deleteInstanceGroup(groupName, "MANAGED");
  });
});
