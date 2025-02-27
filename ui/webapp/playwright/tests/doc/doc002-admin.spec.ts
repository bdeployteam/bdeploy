import { test } from '@bdeploy-setup';
import { AdminPage } from '@bdeploy-pom/primary/admin/admin.page';

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