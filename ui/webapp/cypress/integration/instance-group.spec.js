describe('Instance Group Tests', () => {
  var groupName = 'Demo';

  var mailDomain = '@example.com';

  var globalAdmin = 'global-admin';
  var globalWrite = 'global-write';
  var globalRead = 'global-read';
  var globalNone = 'global-none';

  beforeEach(() => {
    cy.login();
  });

  it('Create instance group', () => {
    cy.createInstanceGroup(groupName);
  });

  it('Upload product to instance group', function() {
    cy.uploadProductIntoGroup(groupName, 'test-product-1-direct.zip');
    cy.uploadProductIntoGroup(groupName, 'test-product-2-direct.zip');
    cy.verifyProductVersion(groupName, "Demo Product", "io.bdeploy/demo", "1.0.0");
    cy.verifyProductVersion(groupName, "Demo Product", "io.bdeploy/demo", "2.0.0");
  });

  it('Create test instance', () => {
    cy.createInstance(groupName, 'InstanceUsingTestProduct').then(uuid => {
      cy.get('body').contains(uuid).should('exist');
    })
  })

  it('Creates user with global capabilities', function() {
    cy.visit('/#/admin/all/(panel:users)');
    cy.waitUntilContentLoaded();

    cy.createUser(globalAdmin, 'Global admin account', globalAdmin + mailDomain, 'demo');
    cy.waitUntilContentLoaded();
    cy.setGlobalCapability(globalAdmin, 'ADMIN');
    cy.waitUntilContentLoaded();

    cy.createUser(globalWrite, 'Global write account', globalWrite + mailDomain, 'demo');
    cy.waitUntilContentLoaded();
    cy.setGlobalCapability(globalWrite, 'WRITE');
    cy.waitUntilContentLoaded();

    cy.createUser(globalRead, 'Global read account', globalRead + mailDomain, 'demo');
    cy.waitUntilContentLoaded();
    cy.setGlobalCapability(globalRead, 'READ');
    cy.waitUntilContentLoaded();

    cy.createUser(globalNone, 'Account without global capabilities', globalNone + mailDomain, 'demo');
  })

  it('Checks permissions on the instance group', function() {
    cy.visit('/#/instancegroup/permissions/' + groupName);
    cy.waitUntilContentLoaded();

    cy.contains('tr', globalAdmin).within(() => {
      cy.get('mat-icon[data-cy="global-read"]').should('exist');
      cy.get('mat-icon[data-cy="global-write"]').should('exist');
      cy.get('mat-icon[data-cy="global-admin"]').should('exist');
    });

    cy.contains('tr', globalWrite).within(() => {
      cy.get('mat-icon[data-cy="global-read"]').should('exist');
      cy.get('mat-icon[data-cy="global-write"]').should('exist');
      cy.get('mat-icon[data-cy="grant-admin"]').should('exist');
    });

    cy.contains('tr', globalRead).within(() => {
      cy.get('mat-icon[data-cy="global-read"]').should('exist');
      cy.get('mat-icon[data-cy="grant-write"]').should('exist');
      cy.get('mat-icon[data-cy="grant-admin"]').should('exist');
    });
  })

  it('Grants/revokes admin and write permissions to globalRead user', function() {
    cy.screenshot('BDeploy_Demo_Permissions_Global');

    cy.contains('tr', globalRead).within(() => {
      // grant admin
      cy.get('mat-icon[data-cy="grant-admin"]').click();
      // check admin + write
      cy.get('mat-icon[data-cy="revoke-write"]').should('exist');
      cy.get('mat-icon[data-cy="revoke-admin"]').should('exist');

      // revoke admin
      cy.get('mat-icon[data-cy="revoke-admin"]').click();
      // check write
      cy.get('mat-icon[data-cy="revoke-write"]').should('exist');
      cy.get('mat-icon[data-cy="grant-admin"]').should('exist');

      // revoke write
      cy.get('mat-icon[data-cy="revoke-write"]').click();
      // check write
      cy.get('mat-icon[data-cy="grant-write"]').should('exist');
      cy.get('mat-icon[data-cy="grant-admin"]').should('exist');
    });
  })

  it('Adds a user', function() {
    cy.contains('button', 'add').click();
    cy.contains('button', 'OK').should('exist').and('be.disabled');

    cy.get('input[placeholder="User to add"]').should('exist').click();
    cy.get('input[placeholder="User to add"]').should('exist').and('have.focus').type(globalNone);

    cy.screenshot('BDeploy_Demo_Permissions_AddUser1');
    cy.contains('button', 'OK').click();

    cy.contains('tr', globalNone).within(() => {
      cy.get('mat-icon[data-cy="revoke-read"]').should('exist');
      cy.get('mat-icon[data-cy="grant-write"]').should('exist');
      cy.get('mat-icon[data-cy="grant-admin"]').should('exist');
    });
    cy.screenshot('BDeploy_Demo_Permissions_AddUser2');
  })

  it('Removes a user', function() {

    cy.contains('tr', globalNone).within(() => {
      cy.contains('mat-icon', 'delete').should('exist');
      cy.contains('mat-icon', 'delete').click();
    });
    cy.contains('tr', globalNone).should('not.exist');
  })

  it('Delete instance group', () => {
    cy.visit('/');
    cy.deleteInstanceGroup(groupName);
  });

});
