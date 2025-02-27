import { expect, test } from '@bdeploy-setup';
import { AdminPage } from '@bdeploy-pom/primary/admin/admin.page';

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
