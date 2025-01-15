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