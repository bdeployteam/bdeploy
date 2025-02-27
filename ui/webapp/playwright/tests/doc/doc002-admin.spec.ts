import { expect, test } from '@bdeploy-setup';
import { AdminPage } from '@bdeploy-pom/primary/admin/admin.page';
import { BackendApi } from '@bdeploy-backend';

test.beforeEach(async ({ standalone }) => {
  const api = new BackendApi(standalone);
  await api.deleteUser('test');
});

test('General Settings', async ({ standalone }) => {
  const admin = new AdminPage(standalone);
  await admin.goto();
  await admin.screenshot('Doc_Admin_Settings');
});

test('Nodes Details', async ({ standalone }) => {
  const admin = new AdminPage(standalone);
  await admin.goto();

  const nodes = await admin.gotoNodesPage();
  await nodes.selectNode('master');

  await admin.screenshot('Doc_Admin_Nodes_Details');
});

test('Add Node', async ({ standalone }) => {
  const admin = new AdminPage(standalone);
  await admin.goto();

  const nodes = await admin.gotoNodesPage();
  await nodes.addNode();

  await admin.screenshot('Doc_Admin_Nodes_Add');
});

test('Convert Node', async ({ standalone }) => {
  const admin = new AdminPage(standalone);
  await admin.goto();

  const nodes = await admin.gotoNodesPage();
  const details = await nodes.selectNode('master');
  await details.convertToNode();

  await admin.screenshot('Doc_Admin_Nodes_Conversion');
});

test('Mail Settings', async ({ standalone }) => {
  const admin = new AdminPage(standalone);
  await admin.goto();

  const sending = await admin.gotoMailSendingTab();
  await sending.edit();
  await sending.screenshot('Doc_Admin_Mail_Sending');

  const receiving = await admin.gotoMailReceivingTab();
  await receiving.edit();
  await receiving.screenshot('Doc_Admin_Mail_Receiving');
});

test('Pooling', async ({ standalone }) => {
  const admin = new AdminPage(standalone);
  await admin.goto();

  const jobs = await admin.gotoJobsPage();
  await jobs.screenshot('Doc_Admin_Jobs');

  const hives = await admin.gotoBHivesPage();
  await hives.screenshot('Doc_Admin_BHive_Browser');

  await hives.getHiveDetails('default');
  await hives.screenshot('Doc_Admin_BHive_Details');
});

test('LDAP Settings', async ({ standalone }) => {
  const admin = new AdminPage(standalone);
  await admin.goto();

  const ldap = await admin.gotoLDAPServersTab();
  const addPanel = await ldap.addServer();
  await addPanel.fill('ldap://localhost:389', 'Company LDAP', 'user', 'password', 'dc=example,dc=com');
  await addPanel.screenshot('Doc_Admin_LDAP_Server_Config');

  await addPanel.apply();
  await expect(ldap.getTableRowContaining('Company LDAP')).toBeVisible();
  await ldap.screenshot('Doc_Admin_LDAP_Servers');
});

test('Plugins', async ({ standalone }) => {
  const admin = new AdminPage(standalone);
  await admin.goto();

  const plugins = await admin.gotoPluginsTab();
  await plugins.screenshot('Doc_Admin_Plugins');
});

test('User Accounts', async ({ standalone }) => {
  const admin = new AdminPage(standalone);
  await admin.goto();

  const accounts = await admin.gotoUserAccountsPage();
  await accounts.screenshot('Doc_Admin_User_Accounts');

  const addPanel = await accounts.getAddUserPanel();
  await addPanel.fill('test', 'Test User', 'test@example.com', 'testX123!123', 'testX123!123');
  await addPanel.screenshot('Doc_Admin_User_Accounts_Add');
  await addPanel.save();

  await expect(accounts.getTableRowContaining('test')).toBeVisible();
  const details = await accounts.getUserDetailsPanel('test');
  await details.deactivate();
  await expect(details.getDialog().getByText('INACTIVE')).toBeVisible();
  await details.screenshot('Doc_Admin_User_Accounts_Inactive');

  await details.activate();
  const perms = await details.getAssignPermissionPanel();
  await perms.screenshot('Doc_Admin_User_Accounts_Permissions');
  await perms.getBackToOverviewButton().click();

  const edit = await details.getEditPanel();
  await edit.screenshot('Doc_Admin_User_Accounts_Edit');

  await edit.getBackToOverviewButton().click();
  await details.delete();
});

test('User Groups', async ({ standalone }) => {
  const admin = new AdminPage(standalone);
  await admin.goto();

  const groups = await admin.gotoUserGroupsPage();
  await groups.screenshot('Doc_Admin_User_Groups');

  const addPanel = await groups.getAddGroupPanel();
  await addPanel.fill('TestGroup', 'This is a test group');
  await addPanel.screenshot('Doc_Admin_User_Groups_Add');
  await addPanel.save();

  await expect(groups.getTableRowContaining('TestGroup')).toBeVisible();
  const details = await groups.getGroupDetailsPanel('TestGroup');
  await details.fillAddUser('adm');
  await details.screenshot('Doc_Admin_User_Groups_Details');

  const perms = await details.getAssignPermissionPanel();
  await perms.screenshot('Doc_Admin_User_Groups_Permissions_Add');

  await perms.getBackToOverviewButton().click();
  await details.delete();
});
