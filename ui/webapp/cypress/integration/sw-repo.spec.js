describe('Software Repository Tests', () => {
  var repoName = 'Demo-Repository';

  var mailDomain = '@example.com';

  var globalAdmin = 'global-admin';
  var globalWrite = 'global-write';
  var globalRead = 'global-read';
  var globalNone = 'global-none';

  beforeEach(() => {
    cy.login();
  });

  it('Create Software Repository', () => {
    cy.visit('/#/softwarerepo/browser');
    cy.waitUntilContentLoaded();

    cy.contains('button', 'add').click();

    cy.get('input[data-placeholder^="Software Repository name"]').type(repoName);
    cy.get('input[data-placeholder=Description]').type('Automated Test Repo ' + repoName);

    cy.contains('button', 'SAVE').click();
    cy.waitUntilContentLoaded();

    cy.contains('mat-card', repoName).should('exist');
  });

  it('Creates user with global permissions', function() {
    cy.visit('/#/admin/all/(panel:users)');
    cy.waitUntilContentLoaded();

    cy.createUser(globalAdmin, 'Global admin account', globalAdmin + mailDomain, 'demo');
    cy.waitUntilContentLoaded();
    cy.setGlobalPermission(globalAdmin, 'ADMIN');
    cy.waitUntilContentLoaded();

    cy.createUser(globalWrite, 'Global write account', globalWrite + mailDomain, 'demo');
    cy.waitUntilContentLoaded();
    cy.setGlobalPermission(globalWrite, 'WRITE');
    cy.waitUntilContentLoaded();

    cy.createUser(globalRead, 'Global read account', globalRead + mailDomain, 'demo');
    cy.waitUntilContentLoaded();
    cy.setGlobalPermission(globalRead, 'READ');
    cy.waitUntilContentLoaded();

    cy.createUser(globalNone, 'Account without global permissions', globalNone + mailDomain, 'demo');
  })

  it('Checks permissions on the Software Repository', function() {
    cy.visit('/#/softwarerepo/permissions/' + repoName);
    cy.waitUntilContentLoaded();

    cy.contains('mat-slide-toggle','Global Access').click();

    cy.contains('tr', globalAdmin).should('exist');
    cy.contains('tr', globalWrite).should('exist');
    cy.contains('tr', globalRead).should('not.exist');
    cy.contains('tr', globalNone).should('not.exist');
  })

  it('Adds a user', function() {
    cy.screenshot('BDeploy_Demo-Repository_Permissions_Global');

    cy.contains('button', 'add').click();
    cy.screenshot('BDeploy_Demo-Repository_Permissions_AddUser1');
    cy.contains('button', 'OK').should('exist').and('be.disabled');

    cy.get('input[data-placeholder="User to add"]').should('exist').click();
    cy.get('input[data-placeholder="User to add"]').should('exist').and('have.focus').type(globalNone);
    cy.contains('button', 'OK').click();

    cy.contains('tr', globalAdmin).should('exist');
    cy.contains('tr', globalWrite).should('exist');
    cy.contains('tr', globalRead).should('not.exist');
    cy.contains('tr', globalNone).should('exist');
    cy.screenshot('BDeploy_Demo-Repository_Permissions_AddUser2');
  })

  it('Un-checks the show-global-user switch', function() {
    cy.get('mat-toolbar').within(() => {
      cy.get('mat-slide-toggle input').should('be.checked');
    });

    cy.contains('mat-slide-toggle', 'Show User with Global Access').click();

    cy.get('mat-toolbar').within(() => {
      cy.get('mat-slide-toggle input').should('not.be.checked');
    });

    cy.contains('tr', globalAdmin).should('not.exist');
    cy.contains('tr', globalWrite).should('not.exist');
    cy.contains('tr', globalRead).should('not.exist');
    cy.contains('tr', globalNone).should('exist');
  })

  it('Removes a user', function() {

    cy.contains('tr', globalNone).within(() => {
      cy.contains('mat-icon', 'delete').should('exist');
      cy.contains('mat-icon', 'delete').click();
    });
    cy.contains('tr', globalNone).should('not.exist');
  })

  it('Deletes the users', () => {
    cy.visit('/#/admin/all/(panel:users)');
    cy.waitUntilContentLoaded();

    cy.deleteUser(globalAdmin);
    cy.deleteUser(globalWrite);
    cy.deleteUser(globalRead);
    cy.deleteUser(globalNone);
  })

  it('Delete Software Repository', () => {
    cy.visit('/#/softwarerepo/browser');
    cy.waitUntilContentLoaded();

    cy.contains('mat-card', repoName)
      .should('exist')
      .clickContextMenuDialog('Delete');
    cy.contains('mat-dialog-container', 'Delete Software Repository: ' + repoName)
      .should('exist')
      .within(dialog => {
        cy.contains('button', 'OK').click();
      });

    cy.contains('mat-card', repoName).should('not.exist');
  });
});
