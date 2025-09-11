import { BackendApi } from '@bdeploy-backend';
import { expect, test } from '@bdeploy-setup';
import { uploadProduct } from '@bdeploy-pom/common/common-tasks';
import { SystemTemplatePage } from '@bdeploy-pom/primary/systems/system-template.page';
import { FormSelectElement } from '@bdeploy-elements/form-select.elements';
import { TestInfo } from '@playwright/test';
import { InstanceConfigurationPage } from '@bdeploy-pom/primary/instances/instance-configuration.page';
import { InstanceDashboardPage } from '@bdeploy-pom/primary/instances/instance-dashboard.page';
import { InstancesBrowserPage } from '@bdeploy-pom/primary/instances/instances-browser.page';

function groupId(testInfo: TestInfo) {
  return `S006Group-${testInfo.workerIndex}`;
}

test.beforeEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.createGroup(groupId(testInfo), `Group (${testInfo.workerIndex}) for S006`);
});

test.afterEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.deleteGroup(groupId(testInfo));
});

/**
 * This is set up to check: how properties are overridden in system vs instance template, and what is defaulted.
 */
test('S006 Creation of a system using a template', async ({ standalone }, testInfo) => {
  await uploadProduct(standalone, groupId(testInfo), 'test-product-2-direct');
  await uploadProduct(standalone, groupId(testInfo), 'chat-product-1-direct');

  const sysTpl = new SystemTemplatePage(standalone, groupId(testInfo));
  await sysTpl.goto();

  await sysTpl.uploadTemplate('system-template.yaml');
  await sysTpl.checkTemplateLoaded('system-template.yaml');

  const nextBtn = sysTpl.getDialog().getByRole('button', { name: 'Next' });
  await nextBtn.click();

  await sysTpl.getDialog().getByLabel('System Name').fill('S006 System');
  await new FormSelectElement(sysTpl.getDialog(), 'Purpose').selectOption('TEST');

  await nextBtn.click();
  await sysTpl.getDialog().getByLabel('The Node Number').fill('2');
  await sysTpl.getDialog().getByLabel('The Node Base Name').fill('Minion');
  await sysTpl.getDialog().getByLabel('System Password').fill('XX');

  await nextBtn.click();

  // fill data for demo instance
  await sysTpl.getDialog().getByLabel('Text Value').fill('Demo Text');
  await new FormSelectElement(sysTpl.getDialog(), 'Group \'Server Apps\'').selectOption('Apply to master');

  // fill data for both chat instances.
  await sysTpl.getDialog().getByRole('tab').getByText('Chat Node 3').click();
  await new FormSelectElement(sysTpl.getDialog(), 'Group \'Chat App\'').selectOption('Apply to master');

  await sysTpl.getDialog().getByRole('tab').getByText('Chat Node 4').click();
  await new FormSelectElement(sysTpl.getDialog(), 'Group \'Chat App\'').selectOption('Apply to master');

  await nextBtn.click();
  await expect(sysTpl.getDialog().locator('app-bd-notification-card', { hasText: 'Instances have been created.' })).toBeVisible();
  await expect(sysTpl.getDialog().locator('table').locator('mat-icon', { hasText: 'check' })).toHaveCount(4);
  await sysTpl.getDialog().getByRole('button', { name: 'Finish' }).click();

  /*--- check all instances appear in the instance browser ---*/
  const instBrowser = new InstancesBrowserPage(standalone, groupId(testInfo));
  await instBrowser.goto();
  await instBrowser.shouldHaveTableRows(5);
  await expect(instBrowser.getTableRowContaining('S006 System')).toBeVisible();

  /*--- Check values for each instance  ---*/
  const chatMasterDashboard = new InstanceDashboardPage(standalone, groupId(testInfo), 'Chat Master');
  await chatMasterDashboard.goto();
  const chatMasterConfig = new InstanceConfigurationPage(standalone, groupId(testInfo), 'Chat Master');
  await chatMasterConfig.goto();
  await chatMasterConfig.shouldHaveNodeCount(1);
  await chatMasterConfig.shouldHaveControlGroupCountForNode('master', 1);
  await chatMasterConfig.shouldHaveProcessCountForNode('master', 1);
  const chatMasterBaseConfig = await chatMasterConfig.goToBaseConfiguration();
  // instance template = unset, system template = true => true
  await chatMasterBaseConfig.getAutomaticStartup().shouldBeChecked();
  // instance template = unset, system template = false => false
  await chatMasterBaseConfig.getAutomaticUninstall().shouldNotBeChecked();

  const chatNode3Dashboard = new InstanceDashboardPage(standalone, groupId(testInfo), 'Chat Node 3');
  await chatNode3Dashboard.goto();
  const chatNode3Config = new InstanceConfigurationPage(standalone, groupId(testInfo), 'Chat Node 3');
  await chatNode3Config.goto();
  await chatNode3Config.shouldHaveNodeCount(1);
  await chatNode3Config.shouldHaveControlGroupCountForNode('master', 1);
  await chatNode3Config.shouldHaveProcessCountForNode('master', 1);
  // check that the Node Base Name is being used
  await expect(chatNode3Config.getProcessRow('master', 'The Chat App Minion3')).toBeVisible();
  const chatNode3BaseConfig = await chatNode3Config.goToBaseConfiguration();
  // this checks defaults
  // instance template = unset, system template = unset => false
  await chatNode3BaseConfig.getAutomaticStartup().shouldNotBeChecked();
  // instance template = unset, system template = unset => true
  await chatNode3BaseConfig.getAutomaticUninstall().shouldBeChecked();

  // skip the other chat node, for now it does not contain anything interesting

  const demoInstanceDashboard = new InstanceDashboardPage(standalone, groupId(testInfo), 'Demo Instance');
  await demoInstanceDashboard.goto();
  const demoInstanceConfig = new InstanceConfigurationPage(standalone, groupId(testInfo), 'Demo Instance');
  await demoInstanceConfig.goto();
  await demoInstanceConfig.shouldHaveNodeCount(2);
  // we did not apply client apps
  await demoInstanceConfig.shouldHaveProcessCountForNode('Client Applications', 0);
  // check 3 control groups were added each with a process
  await demoInstanceConfig.shouldHaveControlGroupCountForNode('master', 3);
  await demoInstanceConfig.shouldHaveProcessCountForNode('master', 3);
  const serverWithSleepSettings = await demoInstanceConfig.getProcessSettingsPanel('master', 'Server With Sleep');
  const serverWithSleepParameters = await serverWithSleepSettings.getConfigureParametersPanel();
  const sleepParameters = await serverWithSleepParameters.getParameterGroup('Sleep Configuration');
  await sleepParameters.toggle();
  await expect(sleepParameters.getParameter("param.sleep").locator("input").nth(0)).toHaveValue("10");
  await serverWithSleepParameters.getBackToOverviewButton().click();

  const demoInstanceBaseConfig = await demoInstanceConfig.goToBaseConfiguration();
  // this checks system template has precedence
  // instance template = true, system template = false => false
  await demoInstanceBaseConfig.getAutomaticStartup().shouldNotBeChecked();
  // instance template = false, system template = true => true
  await demoInstanceBaseConfig.getAutomaticUninstall().shouldBeChecked();
});

