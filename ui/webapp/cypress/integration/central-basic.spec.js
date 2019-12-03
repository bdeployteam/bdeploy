describe("Central/Managed Basic Test", function() {
  // don't use a dynamic name. this variable is re-initialized on /every/ cy.visit().
  var groupName = "CentralGroup-" + new Date().getTime();

  it("visits central", function() {
    cy.visitBDeploy("/", "CENTRAL");
    cy.contains("mat-toolbar", "CENTRAL").within(() => {
      cy.contains("button", "menu").click();
    });
    cy.contains("mat-sidenav", "CENTRAL")
      .should("exist")
      .and("be.visible");
  });

  it("visits managed", function() {
    cy.visitBDeploy("/", "MANAGED");
    cy.contains("mat-toolbar", "MANAGED").within(() => {
      cy.contains("button", "menu").click();
    });
    cy.contains("mat-sidenav", "MANAGED")
      .should("exist")
      .and("be.visible");
  });

  it("creates instance group on central", () => {
    cy.createInstanceGroup(groupName, "CENTRAL");
  });

  it("attach managed server to central", () => {
    cy.attachManaged(groupName);
  });

  it("deletes and re-attaches instance group on managed server", () => {
    cy.deleteInstanceGroup(groupName, "MANAGED");

    cy.visitBDeploy("/", "CENTRAL");

    // instance group is still there on central.
    cy.get('[data-cy="group-' + groupName + '"]')
      .first()
      .should("exist");

    cy.visitBDeploy("/#/servers/browser/" + groupName, "CENTRAL");
    cy.contains("mat-expansion-panel", "Test Local Server")
      .should("exist")
      .and("be.visible")
      .within(e => {
        cy.contains("button", "Synchronize")
          .should("exist")
          .and("be.enabled")
          .click();

        // don't use waitUntilContentLoaded as it does not work in within blocks.
        cy.get("mat-spinner").should("not.exist");
      });

    cy.contains("mat-dialog-container", "Synchronization Error")
      .should("exist")
      .within(dialog => {
        cy.contains("may no longer exist").should("exist");
        cy.contains("button", "OK")
          .should("be.enabled")
          .click();
      });

    cy.contains("mat-expansion-panel", "Test Local Server")
      .should("exist")
      .and("be.visible")
      .within(e => {
        cy.contains("button", "Delete")
          .should("exist")
          .and("be.enabled")
          .click();
      });

    cy.contains("mat-dialog-container", "Delete Managed Server")
      .should("exist")
      .within(dialog => {
        cy.contains("button", "OK")
          .should("be.enabled")
          .click();
      });

    cy.attachManaged(groupName); // re-attach
  });

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
