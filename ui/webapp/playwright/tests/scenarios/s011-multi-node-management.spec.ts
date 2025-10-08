import { test } from '@bdeploy-setup';
import { AdminPage } from '@bdeploy-pom/primary/admin/admin.page';
import { BackendApi } from '@bdeploy-backend';

test.beforeEach(async ({ standalone }) => {
  const api = new BackendApi(standalone);
  await api.removeNodesThatAreNotMaster();
});

test.afterEach(async ({ standalone }) => {
  const api = new BackendApi(standalone);
  await api.removeNodesThatAreNotMaster();
});

test('S011 manage multi-node', async ({ standalone }) => {
  const admin = new AdminPage(standalone);
  await admin.goto();
  const nodes = await admin.gotoNodesPage();
  //should have the master node and the grouping header for standard nodes
  await nodes.shouldHaveTableRows(2);

  // Add nodes
  var addPanel = await nodes.addMultiNode();
  await addPanel.fill('MyNode', 'LINUX');
  await addPanel.save();
  await nodes.shouldHaveTableRows(4);

  addPanel = await nodes.addMultiNode();
  await addPanel.fill('MyNode2', 'WINDOWS');
  await addPanel.save();
  await nodes.shouldHaveTableRows(5);

  // Check Node Details
  const detailsPanel = await nodes.selectMultiNode('MyNode');
  await detailsPanel.verifyDetails('MyNode - LINUX');

  // Remove the Node
  await detailsPanel.remove('MyNode');
  await nodes.shouldHaveTableRows(4);
});
