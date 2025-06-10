import { TestInfo } from '@playwright/test';
import { expect, test } from '@bdeploy-setup';
import { InstanceGroupsBrowserPage } from '@bdeploy-pom/primary/groups/groups-browser.page';
import { ManagedServersPage } from '@bdeploy-pom/primary/groups/managed-servers.page';
import { BackendApi } from '@bdeploy-backend';
import { createInstance, uploadProduct } from '@bdeploy-pom/common/common-tasks';
import { InstancePurpose } from '@bdeploy/models/gen.dtos';
import { InstancesBrowserPage } from '@bdeploy-pom/primary/instances/instances-browser.page';
import { GroupsProductsPage } from '@bdeploy-pom/primary/groups/groups-products.page';
import { InstanceDashboardPage } from '@bdeploy-pom/primary/instances/instance-dashboard.page';
import { InstanceConfigurationPage } from '@bdeploy-pom/primary/instances/instance-configuration.page';

function groupId(testInfo: TestInfo) {
  return `ManagedGroup-${testInfo.workerIndex}`;
}

test.beforeEach(async ({ central, managed }, testInfo) => {
  const api = new BackendApi(central);
  await api.deleteGroup(groupId(testInfo));

  const managedApi = new BackendApi(managed);
  await managedApi.deleteGroup(groupId(testInfo));

  await api.createGroup(groupId(testInfo), `Group (${testInfo.workerIndex}) for central/managed tests`);
});

test.afterEach(async ({ central, managed }, testInfo) => {
  const api = new BackendApi(central);
  await api.deleteGroup(groupId(testInfo));

  const managedApi = new BackendApi(managed);
  await managedApi.deleteGroup(groupId(testInfo));
});

test('Managed Central Link', async ({managed, central}, testInfo) => {
  // must filter list of groups so other tests don't interfere with the linking panel.
  const managedApi = new BackendApi(managed);
  await managedApi.mockFilterGroups(groupId(testInfo));

  const groups = new InstanceGroupsBrowserPage(managed);
  await groups.goto();

  await groups.screenshot('Doc_ManagedEmpty');

  const link = await groups.linkInstanceGroup();
  await link.screenshot('Doc_ManagedLinkGroup');

  await link.toggleOfflineLinking();
  await link.downloadOfflineLinking('temp-link-managed.json');

  const centralServers = new ManagedServersPage(central, groupId(testInfo));
  await centralServers.goto();
  await centralServers.screenshot('Doc_CentralEmptyServers');

  const linkServer = await centralServers.getLinkServerPanel();
  await linkServer.screenshot('Doc_CentralLinkServer');

  await linkServer.fillManagedInfo('temp-link-managed.json', 'Description of server', 'https://localhost:7716/api/');
  await linkServer.screenshot('Doc_CentralLinkServerFilled');

  await linkServer.save();
  await expect(centralServers.getTableRowContaining('Description of server')).toBeVisible();
  await linkServer.expectClosed();
  await centralServers.screenshot('Doc_CentralLinkDone');
});

test('Managed Central Sync', async ({managed, central}, testInfo) => {
  const centralApi = new BackendApi(central);
  const managedApi = new BackendApi(managed);

  await centralApi.attachManaged(groupId(testInfo), managedApi, 'https://localhost:7716/api/');

  await uploadProduct(central, groupId(testInfo), 'test-product-1-direct');
  await uploadProduct(managed, groupId(testInfo), 'test-product-2-direct');

  await createInstance(central, groupId(testInfo), 'TestInstance-1', 'Sync Test Instance 1', InstancePurpose.TEST, 'Demo Product', '1.0.0', 'localhost');
  await createInstance(managed, groupId(testInfo), 'TestInstance-2', 'Sync Test Instance 2', InstancePurpose.TEST, 'Demo Product', '2.0.0');

  const instances = new InstancesBrowserPage(central, groupId(testInfo));
  await instances.goto();
  await instances.syncAll();
  await instances.screenshot('Doc_CentralInstanceList');

  const products = new GroupsProductsPage(central, groupId(testInfo));
  await products.goto();

  const syncMode = await products.openProductSyncPanel();
  await syncMode.screenshot('Doc_CentralProdSync');

  const syncServer = await syncMode.downloadProduct();
  await syncServer.screenshot('Doc_CentralProdSyncServer');

  const syncVersion = await syncServer.selectServer('localhost');
  await syncVersion.selectVersion('2.0.0');

  await syncVersion.screenshot('Doc_CentralProdSyncVersion');
  await syncVersion.transfer();
  await expect(products.getProductRow('2.0.0')).toBeAttached();

  const dashboard = new InstanceDashboardPage(central, groupId(testInfo), 'TestInstance-2');
  await dashboard.goto();
  await dashboard.screenshot('Doc_CentralInstanceDashboard');

  // await dashboard.synchronize();

  const config = new InstanceConfigurationPage(central, groupId(testInfo), 'TestInstance-2');
  await config.goto();
  await config.screenshot('Doc_CentralInstanceConfiguration');
})