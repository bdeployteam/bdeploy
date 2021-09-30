//@ts-check

describe('Groups Tests (Permissions)', () => {
  var groupName = 'Demo';

  before(() => {
    cy.cleanAllGroups();
  });

  beforeEach(() => {
    cy.login();
  });

  it('Creates a group', () => {
    cy.visit('/');
    cy.createGroup(groupName);
  });

  // Permission tests:
  // TODO create user with global permissions / ensure that these user exist
  // TODO check global permissions on instance group
  // TODO grant/revoke admin and write permissions to globalRead user

  it('Deletes the group', () => {
    cy.deleteGroup(groupName);
  });
});
