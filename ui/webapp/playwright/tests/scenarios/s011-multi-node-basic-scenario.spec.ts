import { test } from '@bdeploy-setup';
import { AdminPage } from '@bdeploy-pom/primary/admin/admin.page';
import { BackendApi } from '@bdeploy-backend';
import { expect, TestInfo } from '@playwright/test';
import { createInstance, uploadProduct } from '@bdeploy-pom/common/common-tasks';
import { InstancePurpose } from '@bdeploy/models/gen.dtos';
import { InstanceDashboardPage } from '@bdeploy-pom/primary/instances/instance-dashboard.page';
import { InstanceConfigurationPage } from '@bdeploy-pom/primary/instances/instance-configuration.page';

function groupId(testInfo: TestInfo) {
  return `S011Group-${testInfo.workerIndex}`;
}

test.beforeEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.removeNodesThatAreNotMaster();
  await api.createGroup(groupId(testInfo), `Group (${testInfo.workerIndex}) for S011`);
});

test.afterEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.removeNodesThatAreNotMaster();
  await api.deleteGroup(groupId(testInfo));
});

test('S011 Multi-node node without any runtime nodes', async ({ standalone }, testInfo) => {
  const admin = new AdminPage(standalone);
  await admin.goto();

  // ------------------ NODE MANAGEMENT ---------------------
  // Add and remove nodes to check that the management screen works
  const nodes = await admin.gotoNodesPage();
  //should have the master node and the grouping header for standard nodes
  await nodes.shouldHaveTableRows(2);

  var addPanel = await nodes.addMultiNode();
  await addPanel.fill('MyNode', 'LINUX');
  await addPanel.save();
  await nodes.shouldHaveTableRows(4);

  addPanel = await nodes.addMultiNode();
  await addPanel.fill('MyNode2', 'WINDOWS');
  await addPanel.save();
  await nodes.shouldHaveTableRows(5);

  const detailsPanel = await nodes.selectMultiNode('MyNode');
  await detailsPanel.verifyDetails('MyNode - LINUX');

  await detailsPanel.remove('MyNode');
  await nodes.shouldHaveTableRows(4);

  // ------------------ CREATE INSTANCE ---------------------
  // Add instance and check all can be used
  await uploadProduct(standalone, groupId(testInfo), 'test-product-1-direct');
  await createInstance(standalone, groupId(testInfo), 'Test Instance', 'Test Instance', InstancePurpose.TEST, 'Demo Product', '1.0.0');

  const instanceDashboard = new InstanceDashboardPage(standalone, groupId(testInfo), 'Test Instance');
  await instanceDashboard.goto();

  // create instance with one process on multi node and one on master
  const instanceConfig = new InstanceConfigurationPage(standalone, groupId(testInfo), 'Test Instance');
  await instanceConfig.goto();

  let settingsPanel = await instanceConfig.getSettingsPanel();
  const nodesPanel = await settingsPanel.getManageNodesPanel();
  await nodesPanel.selectNode('MyNode2');
  await nodesPanel.apply();

  let addProcessPanel = await instanceConfig.getAddProcessPanel('MyNode2');
  await addProcessPanel.addProcess('Server Application');

  let masterProcessPanel = await instanceConfig.getAddProcessPanel('master');
  await masterProcessPanel.addProcess('Server Application');
  const masterProcessSettings = await  instanceConfig.getProcessSettingsPanel('master', 'Server Application');
  const masterProcessConfigPanel = await masterProcessSettings.getConfigureParametersPanel();
  await masterProcessConfigPanel.fillProcessName('App on Master');
  await masterProcessConfigPanel.apply();
  await instanceConfig.save();

  // activate and check it shows
  await standalone.waitForURL(`/#/instances/dashboard/${groupId(testInfo)}/**`);
  await instanceDashboard.expectOpen();
  await instanceDashboard.install('2');
  await instanceDashboard.activate();

  await instanceDashboard.shouldHaveServerNodeCount(1);
  await instanceDashboard.shouldHaveProcessCountForNode('master', 1);
  await instanceDashboard.shouldHaveMultiNodeCount(1);
  await instanceDashboard.shouldHaveProcessCountForNode('MyNode2', 1);

  // check that the process panel appears even without runtime nodes
  const multiProcessPanel = await instanceDashboard.getMultiNodeProcessStatus('MyNode2', 'Server Application');
  await expect(multiProcessPanel.getGoToConfig()).toBeEnabled();

  await expect(multiProcessPanel.getStatusReport().locator('div.mat-expansion-panel-body > div > :nth-child(3n)', { hasText: '0' }))
    .toHaveCount(10);
  await expect(multiProcessPanel.getPortStates().locator('div.mat-expansion-panel-body > div > :nth-child(3n)', { hasText: '0' }))
    .toHaveCount(2);
  await expect(multiProcessPanel.getActualityStatus().locator('div.mat-expansion-panel-body > div > :nth-child(2n)', { hasText: '0' }))
    .toHaveCount(2);

  // ------------------ Bug scenario 1 ---------------------
  // navigate to master process status then multi node config to check no errors appears
  await instanceDashboard.getProcessStatus('master', 'App on Master');

  await instanceDashboard.getMultiNodeProcessStatus('MyNode2', 'Server Application');
  await multiProcessPanel.getGoToConfig().click();
  await instanceConfig.getProcessSettingsPanel('MyNode2', 'Server Application');
  await expect(standalone.locator(".error-snackbar")).toHaveCount(0);
  // end bug
});
