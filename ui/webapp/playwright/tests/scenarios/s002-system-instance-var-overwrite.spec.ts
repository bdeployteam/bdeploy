import { expect, test } from '@bdeploy-setup';
import { createInstance, uploadProduct } from '@bdeploy-pom/common/common-tasks';
import { InstancePurpose } from '@bdeploy/models/gen.dtos';
import { InstanceDashboardPage } from '@bdeploy-pom/primary/instances/instance-dashboard.page';
import { InstanceConfigurationPage } from '@bdeploy-pom/primary/instances/instance-configuration.page';
import { SystemBrowserPage } from '@bdeploy-pom/primary/systems/system-browser.page';
import { TestInfo } from '@playwright/test';
import { BackendApi } from '@bdeploy-backend';

function groupId(testInfo: TestInfo) {
    return `S002Group-${testInfo.workerIndex}`;
}

test.beforeEach(async ({ standalone }, testInfo) => {
    const api = new BackendApi(standalone);
    await api.createGroup(groupId(testInfo), `Group (${testInfo.workerIndex}) for S002`);
});

test.afterEach(async ({ standalone }, testInfo) => {
    const api = new BackendApi(standalone);
    await api.deleteGroup(groupId(testInfo));
});

test('S002 Instance variables are overwritten by system variables', async ({ standalone }, testInfo) => {
    const page = standalone;
    const group = groupId(testInfo);

    await uploadProduct(page, group, 'test-product-2-direct');

    const systems = new SystemBrowserPage(page, group)
    await systems.goto();
    const addSystemPanel = await systems.addSystem();
    await addSystemPanel.fill('system', 'System');
    await addSystemPanel.save();
    await expect(systems.getTableRowContaining('system')).toBeAttached();

    await createInstance(page, group, 'TestInstance', 'Test Instance', InstancePurpose.TEST, 'Demo Product', '2.0.0', null, null, null, null, 'system');
    const instance = new InstanceDashboardPage(page, group, 'TestInstance');
    await instance.goto();
    const config = new InstanceConfigurationPage(page, group, 'TestInstance');
    await config.goto();

    const settings = await config.getSettingsPanel();
    const varPanel = await settings.getInstanceVariablePanel();

    // create custom.var instance variable with value "instanceVariableValue" and custom.ref to references it. custom.ref preview "instanceVariableValue"
    const customVarGroup = await varPanel.getVariableGroup('Custom Variables');
    await customVarGroup.toggle();
    await customVarGroup.createCustomVariable('custom.var', 'instanceVariableValue');
    await customVarGroup.createCustomVariable('custom.ref', '{{X:custom.var}}');
    await customVarGroup.checkPreview('custom.ref', 'instanceVariableValue');
    await varPanel.apply()
    await config.save();

    // create custom.var system variable with value "systemVariableValue"
    await systems.goto();    
    const systemDetails = await systems.getSystemDetailsPanel('system');
    const systemVarPanel = await systemDetails.getSystemVariablePanel();
    const systemCustomVarGroup = await systemVarPanel.getVariableGroup('Custom Variables');
    await systemCustomVarGroup.toggle();
    await systemCustomVarGroup.createCustomVariable('custom.var', 'systemVariableValue');
    await systemVarPanel.save();

    // go back to instance variables
    await instance.goto();
    await config.goto();
    await config.getSettingsPanel();
    await settings.getInstanceVariablePanel();

    // custom.var is disabled and ovewritten. custom.ref has preview "systemVariableValue"
    await customVarGroup.toggle();
    await customVarGroup.checkPreview('custom.ref', 'systemVariableValue');
    await expect(page.getByTestId('custom.var').getByRole('textbox', { name: 'custom.var' })).toBeDisabled();
    await expect(page.getByText('Variable custom.var is set through system variable')).toBeVisible();
});