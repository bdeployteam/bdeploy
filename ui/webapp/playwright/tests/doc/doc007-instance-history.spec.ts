import { expect, test } from '@bdeploy-setup';
import { TestInfo } from '@playwright/test';
import { BackendApi } from '@bdeploy-backend';
import { createInstance, uploadProduct } from '@bdeploy-pom/common/common-tasks';
import { InstancePurpose } from '@bdeploy/models/gen.dtos';
import { InstanceConfigurationPage } from '@bdeploy-pom/primary/instances/instance-configuration.page';
import { InstanceDashboardPage } from '@bdeploy-pom/primary/instances/instance-dashboard.page';
import { InstanceHistoryPage } from '@bdeploy-pom/primary/instances/instance-history.page';

test.slow();

function groupId(testInfo: TestInfo) {
  return `HistGroup-${testInfo.workerIndex}`;
}

test.beforeEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.createGroup(groupId(testInfo), `Group (${testInfo.workerIndex}) for history tests`);
});

test.afterEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.deleteGroup(groupId(testInfo));
});

test('Instance History', async ({ standalone }, testInfo) => {
  await uploadProduct(standalone, groupId(testInfo), 'test-product-2-direct');
  await createInstance(standalone, groupId(testInfo), 'History Test Instance', 'History Test', InstancePurpose.TEST, 'Demo Product', '2.0.0');

  const config = new InstanceConfigurationPage(standalone, groupId(testInfo), 'History Test Instance');
  await config.goto();
  const addPanel = await config.getAddProcessPanel('master');
  await addPanel.addProcess('Server Application');

  await config.waitForValidation();
  await config.save();
  await config.goto();

  const settings = await config.getProcessSettingsPanel('master', 'Server Application');
  const params = await settings.getConfigureParametersPanel();
  const sleepCfg = await params.getParameterGroup('Sleep Configuration');
  await sleepCfg.toggle();
  await sleepCfg.selectParameters();
  await sleepCfg.getParameter('param.sleep').locator('mat-icon', { hasText: 'add' }).click();
  await sleepCfg.finishSelectParameters();
  await sleepCfg.getParameter('param.sleep').locator('id=param.sleep_val').fill('5');
  await params.apply();

  await config.waitForValidation();
  await config.save();

  const dashboard = new InstanceDashboardPage(standalone, groupId(testInfo), 'Data Files Instance');

  // TODO: sigh.. again that sleep required.
  await standalone.waitForTimeout(200);
  await dashboard.install();
  await dashboard.activate();

  // run a process to fill the runtime history as well.
  const status = await dashboard.getProcessStatus('master', 'Server Application');
  await status.start();

  // finally go look at the history while the process is happily restarting a little :)
  const history = new InstanceHistoryPage(standalone, groupId(testInfo), 'History Test Instance');
  await history.goto();

  await history.screenshot('Doc_History');

  const entryTwo = await history.getEntryDetailPanel('Version 2: Created');
  await entryTwo.screenshot('Doc_HistoryEntry');

  const compare = await entryTwo.getCompareWithCurrentPanel();
  await compare.getProcessCompare('Server Application').first().scrollIntoViewIfNeeded();
  await compare.screenshot('Doc_HistoryCompare');

  await compare.getBackToOverviewButton().click();
  await entryTwo.getCloseButton().click();

  await history.toggleDeployment();
  await expect(history.getTableRowContaining('Version 3: Installed')).toBeVisible();
  await history.screenshot('Doc_HistoryDeployment');
  await history.toggleDeployment();

  await history.toggleRuntime();
  await expect(history.getTableRowContaining('Server Application is alive')).toBeVisible();
  await history.screenshot('Doc_HistoryRuntime');
});