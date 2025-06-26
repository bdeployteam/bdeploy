import { BackendApi } from '@bdeploy-backend';
import { expect, test } from '@bdeploy-setup';
import { createInstance, uploadProduct } from '@bdeploy-pom/common/common-tasks';
import { TestInfo } from '@playwright/test';
import { InstanceConfigurationPage } from '@bdeploy-pom/primary/instances/instance-configuration.page';
import { InstancePurpose } from '@bdeploy/models/gen.dtos';
import { InstanceHistoryPage } from '@bdeploy-pom/primary/instances/instance-history.page';
import { InstanceDashboardPage } from '@bdeploy-pom/primary/instances/instance-dashboard.page';
import { AdminPage } from '@bdeploy-pom/primary/admin/admin.page';

function groupId(testInfo: TestInfo) {
  return `S008Group-${testInfo.workerIndex}`;
}

test.beforeEach(async ({ central, managed }, testInfo) => {
  const apiCentral = new BackendApi(central);
  await apiCentral.createGroup(groupId(testInfo), `Group (${testInfo.workerIndex}) for S008`);

  const apiManaged = new BackendApi(managed);
  await apiCentral.attachManaged(groupId(testInfo), apiManaged, 'https://localhost:7716/api/');
});

test.afterEach(async ({ central, managed }, testInfo) => {
  const apiCentral = new BackendApi(central);
  await apiCentral.deleteGroup(groupId(testInfo));

  const apiManaged = new BackendApi(managed);
  await apiManaged.deleteGroup(groupId(testInfo));
});

test('S008 Sync deleted instance version', async ({ central, managed }, testInfo) => {
  await uploadProduct(central, groupId(testInfo), 'test-product-2-direct');
  const instanceId = await createInstance(central, groupId(testInfo), 'Demo Instance', 'S008 test', InstancePurpose.PRODUCTIVE, 'Demo Product', '2.0.0', 'localhost');

  const instanceConfig = new InstanceConfigurationPage(central, groupId(testInfo), 'Demo Instance');
  await instanceConfig.goto();

  let addProcessPanel = await instanceConfig.getAddProcessPanel('master');
  await addProcessPanel.addProcess('Server Application');
  await instanceConfig.waitForValidation();
  await instanceConfig.save();

  await central.waitForURL(`/#/instances/dashboard/${groupId(testInfo)}/**`);

  // delete version on managed
  const managedHist = new InstanceHistoryPage(managed, groupId(testInfo), 'Demo Instance');
  await managedHist.goto();

  const entryTwo = await managedHist.getEntryDetailPanel('Version 2: Created');
  const confirmation = await entryTwo.deleteVersion();
  await confirmation.fill('I UNDERSTAND');
  await confirmation.yes();

  await expect(managedHist.getTableRowContaining('Version 2: Created')).not.toBeAttached();

  // sync on central
  const dashboard = new InstanceDashboardPage(central, groupId(testInfo), 'Demo Instance');
  await dashboard.goto();
  await dashboard.synchronize();

  // verify that node manifests have been removed in bhive browser
  const admin = new AdminPage(central);
  await admin.goto();

  const bhives = await admin.gotoBHivesPage();
  const details = await bhives.getHiveDetails(groupId(testInfo));
  const browser = await details.browseContent();

  await expect(browser.getTableRowContaining(`${instanceId}/root:1`)).toBeAttached();
  await expect(browser.getTableRowContaining(`${instanceId}/root:2`)).not.toBeAttached();
  await expect(browser.getTableRowContaining(`${instanceId}/master:2`)).not.toBeAttached();
});

