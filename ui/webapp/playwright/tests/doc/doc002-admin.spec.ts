import { expect, test } from '@bdeploy-setup';
import { AdminPage } from '@bdeploy-pom/primary/admin/admin.page';
import { BackendApi } from '@bdeploy-backend';
import { TestInfo } from '@playwright/test';
import { createInstance, uploadProduct } from '@bdeploy-pom/common/common-tasks';
import { InstancePurpose } from '@bdeploy/models/gen.dtos';
import { InstanceConfigurationPage } from '@bdeploy-pom/primary/instances/instance-configuration.page';

function groupId(testInfo: TestInfo) {
  return `AdmGroup-${testInfo.workerIndex}`;
}

test.beforeAll(async ({ standalone }) => {
  const api = new BackendApi(standalone);
  await api.deleteUser('test');
});

test.beforeEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.deleteGroup(groupId(testInfo));
  await api.createGroup(groupId(testInfo), `Group (${testInfo.workerIndex}) for admin tests`);
});

test.afterEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.deleteGroup(groupId(testInfo));
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

test('Add Multi-Node', async ({ standalone }) => {
  const admin = new AdminPage(standalone);
  await admin.goto();

  const nodes = await admin.gotoNodesPage();
  await nodes.addMultiNode();

  await admin.screenshot('Doc_Admin_Multi_Nodes_Add');
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
  await addPanel.screenshot('Doc_Admin_Ldap_Server_Config');

  await addPanel.apply();
  await expect(ldap.getTableRowContaining('Company LDAP')).toBeVisible();
  await ldap.screenshot('Doc_Admin_Ldap_Servers');
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
  await perms.screenshot('Doc_Admin_User_Accounts_Permissions_Add');
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
  await details.screenshot('Doc_Admin_User_Groups_Add_Test_User');

  const perms = await details.getAssignPermissionPanel();
  await perms.screenshot('Doc_Admin_User_Groups_Permissions_Add');

  await perms.getBackToOverviewButton().click();
  await details.delete();
});

test('Manual Cleanup', async ({ standalone }, testInfo) => {
  await uploadProduct(standalone, groupId(testInfo), 'test-product-2-direct');
  await createInstance(standalone, groupId(testInfo), 'Admin Instance', 'Test for cleanup', InstancePurpose.TEST, 'Demo Product', '2.0.0');

  const config = new InstanceConfigurationPage(standalone, groupId(testInfo), 'Admin Instance');
  await config.goto();
  const settings = await config.getSettingsPanel();
  await settings.delete();

  // we do this here although it is an admin topic to have "something" to clean up...
  const admin = new AdminPage(standalone);
  await admin.goto();
  await standalone.reload(); // depending on timing a snackbar MAY show up.

  const cleanup = await admin.gotoManualCleanupPage();
  await cleanup.screenshot('Doc_Cleanup');

  await cleanup.calculate();
  await expect(cleanup.getDialog().locator('mat-tab-header')).toBeVisible();
  await cleanup.screenshot('Doc_Cleanup_Actions');
});

test('BDeploy Update', async ({ standalone }) => {
  const admin = new AdminPage(standalone);
  await admin.goto();

  const updates = await admin.gotoBDeployUpdatePage();
  const details = await updates.getUpdateDetailPanel('installed');

  await details.screenshot('Doc_System_BDeploy_Update');
});

