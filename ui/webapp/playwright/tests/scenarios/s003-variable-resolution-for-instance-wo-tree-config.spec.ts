import { expect, test } from '@bdeploy-setup';
import { LoginPage } from '@bdeploy-pom/primary/login.page';
import { createInstance, createInstanceGroup, uploadProduct } from '@bdeploy-pom/common/common-tasks';
import { InstancePurpose } from '@bdeploy/models/gen.dtos';
import { InstanceDashboardPage } from '@bdeploy-pom/primary/instances/instance-dashboard.page';
import { InstanceConfigurationPage } from '@bdeploy-pom/primary/instances/instance-configuration.page';
import { SystemBrowserPage } from '@bdeploy-pom/primary/systems/system-browser.page';
import { randomUUID } from 'crypto';
import { BackendApi } from '@bdeploy-backend';
import { TestInfo } from '@playwright/test';

function groupId(testInfo: TestInfo) {
  return `S003Group-${testInfo.workerIndex}`;
}

test.beforeEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.createGroup(groupId(testInfo), `Group (${testInfo.workerIndex}) for S002`);
});

test.afterEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.deleteGroup(groupId(testInfo));
});

test('S003 Variable resolution for instance without tree config (CT_BDEPLOY-515)', async ({ standalone }, testInfo) => {
  const page = standalone;
  const group = groupId(testInfo);

  /*--- Step 1 -  Upload product ---*/
  await uploadProduct(page, group, 'chat-product-1-direct');

  /*--- Step 2.1 -  Create System ---*/
  const systemName = 'system';
  const systems = new SystemBrowserPage(page, group);
  await systems.goto();
  const addSystemPanel = await systems.addSystem();
  await addSystemPanel.fill(systemName, 'System');
  await addSystemPanel.save();
  await expect(systems.getTableRowContaining(systemName)).toBeAttached();

  /*--- Step 2.2 - Add system variable ---*/
  // create system.var system variable with value "systemVariableValue"
  const systemDetails = await systems.getSystemDetailsPanel(systemName);
  const systemVarPanel = await systemDetails.getSystemVariablePanel();
  const systemCustomVarGroup = await systemVarPanel.getVariableGroup('Custom Variables');
  await systemCustomVarGroup.toggle();
  await systemCustomVarGroup.createCustomVariable('system.var', 'systemVariableValue');
  await systemVarPanel.save(false);

  /*--- Step 3.1 - Create Instance for chat product, using created system ---*/
  const instanceName = 'TestInstance';
  await createInstance(page, group, instanceName, 'Test Instance', InstancePurpose.TEST, 'Demo Chat App', '1.0.0', null, null, null, null, systemName);
  const instance = new InstanceDashboardPage(page, group, instanceName);
  await instance.goto();
  const config = new InstanceConfigurationPage(page, group, instanceName);
  await config.goto();

  /*--- Step 3.2 - Add instance variable ---*/
  const settings = await config.getSettingsPanel();
  const varPanel = await settings.getInstanceVariablePanel();
  const customVarGroup = await varPanel.getVariableGroup('Custom Variables');
  await customVarGroup.toggle();
  // create instance.var instance variable with value "instanceVariableValue" and custom.ref to references it.
  await customVarGroup.createCustomVariable('instance.var', 'instanceVariableValue');
  await varPanel.apply();

  /*--- Step 4.1 - Add process and configure parameters with link expression ---*/
  let process = await config.getAddProcessPanel('master');
  const processName = 'Chat Application';
  await process.addProcess(processName);
  const processSettings = await config.getProcessSettingsPanel('master', processName);
  const paramPanel = await processSettings.getConfigureParametersPanel();
  const processCustomParameters = await paramPanel.getParameterGroup('Custom Parameters');
  await processCustomParameters.toggle();

  /*--- Step 4.2 - Configure parameters that link to system and instance variables ---*/
  const popupForInstanceVariable = await processCustomParameters.addCustomParameter();
  await popupForInstanceVariable.fill('custom.instance', '{{X:instance.var}}');
  await popupForInstanceVariable.ok();

  const popupForSystemVariable = await processCustomParameters.addCustomParameter();
  await popupForSystemVariable.fill('custom.system', '{{X:system.var}}');
  await popupForSystemVariable.ok();

  await paramPanel.apply();
  await config.save();

  /*--- TEST that installation can be done successfully ---*/
  await instance.goto();
  await instance.install('2');
  await expect(instance.getInstallButton()).toBeDisabled();
  await expect(instance.getActivateButton()).toBeEnabled();
});
