import { expect, test } from '@bdeploy-setup';
import { InstanceGroupsBrowserPage } from '@bdeploy-pom/primary/groups/groups-browser.page';
import { GroupSortCard } from '@bdeploy-pom/fragments/group-sort-card.fragment';
import { BackendApi } from '@bdeploy-backend';
import { AdminPage } from '@bdeploy-pom/primary/admin/admin.page';
import { InstancesBrowserPage } from '@bdeploy-pom/primary/instances/instances-browser.page';
import { createInstanceGroup } from '@bdeploy-pom/common/common-tasks';
import { waitForInstanceGroup } from '@bdeploy-pom/common/common-functions';
import { GroupsProductsPage } from '@bdeploy-pom/primary/groups/groups-products.page';

const groupId = `DemoGroup`;
const groupIdTwo = `DemoGroupTwo`;
const groupUser = 'groupUser';

// tests build upon each other in this file
test.describe.configure({ mode: 'serial' });

test.beforeAll(async ({ standalone }) => {
  const api = new BackendApi(standalone);
  await api.deleteGroup(groupId);
  await api.deleteGroup(groupIdTwo);
  await api.deleteUser(groupUser);
});

test.afterAll(async ({ standalone }) => {
  const api = new BackendApi(standalone);
  await api.deleteGroup(groupId);
  await api.deleteGroup(groupIdTwo);
  await api.deleteUser(groupUser);
});

test('Create Instance Group', async ({ standalone }) => {
  const api = new BackendApi(standalone);
  await api.deleteGroup(groupId);
  await api.mockFilterGroups(groupId);

  const groups = new InstanceGroupsBrowserPage(standalone);
  await groups.goto();
  await groups.screenshot('Doc_EmptyGroups');

  const panel = await groups.addInstanceGroup();
  await expect(panel.getTitle()).toContainText('Add Instance Group');

  await groups.screenshot('Doc_AddGroupPanelEmpty');
  await panel.fill(groupId, groupId, 'This is a demo instance group', true, 'bdeploy.png');
  await groups.screenshot('Doc_AddGroupPanelFilled');

  const saveRq = api.waitForGroupPut();
  await panel.save();

  await expect(panel.getDialog()).not.toBeAttached();
  await saveRq;

  await waitForInstanceGroup(groups, groupId);
});

test('Group Permissions', async ({ standalone }) => {
  const groups = new InstanceGroupsBrowserPage(standalone);
  await groups.goto();
  await groups.screenshot('Doc_DemoGroup');

  // need to create a temp user for those.
  const admin = new AdminPage(standalone);
  await admin.goto();
  const acc = await admin.gotoUserAccountsPage();
  const add = await acc.getAddUserPanel();
  await add.fill(groupUser, 'Group User', 'group@example.com', 'pass!123123!', 'pass!123123!');
  await add.save();

  const instances = new InstancesBrowserPage(standalone, groupId);
  await instances.goto();

  const settings = await instances.getGroupSettings();
  await settings.screenshot('Doc_GroupSettings');

  const perm = await settings.getPermissionsPanel();
  await perm.screenshot('Doc_GroupPermGlobalOnly');

  const popup = await perm.getModifyPopup(groupUser);
  await popup.selectPermission('WRITE');
  await popup.screenshot('Doc_GroupPermSetWrite');
  await popup.ok();

  await expect(perm.getTableRowContaining(groupUser).getByText('WRITE')).toBeVisible();
  await perm.screenshot('Doc_GroupPermAssigned');
});

test('Card View', async ({ standalone }) => {
  await new BackendApi(standalone).mockFilterGroups(groupId);
  const groups = new InstanceGroupsBrowserPage(standalone);
  await groups.goto();

  await waitForInstanceGroup(groups, groupId);
  await groups.screenshot('Doc_ModeTable');

  const cardBtn = groups.getToolbar().getByLabel('Toggle Card Mode');
  await expect(cardBtn).not.toHaveClass(/.*bd-toggle-highlight.*/);

  await cardBtn.click();
  await expect(cardBtn).toHaveClass(/.*bd-toggle-highlight.*/);

  const groupCard = groups.getDialog().locator('app-bd-data-card', { hasText: groupId });
  await expect(groupCard).toBeVisible();

  await groups.screenshot('Doc_ModeCards');
  await cardBtn.click();
});

test('Global Attributes', async ({ standalone }) => {
  const api = new BackendApi(standalone);
  await api.deleteGroup(groupIdTwo);
  await api.mockFilterGroups(groupId, groupIdTwo);

  // we do the admin screenshot here as well, since this runs serially and we don't
  // want to synchronize with the admin spec.
  const admin = new AdminPage(standalone);
  await admin.goto();
  const tab = await admin.gotoGlobalAttributesTab();

  // remove the attribute if it is already there.
  const attrRow = tab.getTableRowContaining('Test Attribute');
  if (await attrRow.isVisible()) {
    await attrRow.getByLabel('Remove').click();
    await tab.save();
  }

  const addPanel = await tab.addAttribute();
  await addPanel.fill('Test Attribute', 'This is a test attribute');
  await addPanel.save();

  await expect(tab.getTableRowContaining('Test Attribute')).toBeVisible();
  await admin.screenshot('Doc_Admin_Global_Attributes');

  // still need to save the settings all together
  await tab.save();

  // now create another instance group
  await createInstanceGroup(standalone, groupIdTwo);

  // assign attributes to at least one of them.
  const instances = new InstancesBrowserPage(standalone, groupId);
  await instances.goto();

  const settingsPanel = await instances.getGroupSettings();
  const valuePanel = await settingsPanel.getAttributeValuesPanel();
  const dlg = await valuePanel.addAttributeValue();
  await valuePanel.fillAttributeValueDialog(dlg, 'This is a test attribute', 'Test Value');
  await valuePanel.screenshot('Doc_SetGlobalAttributeValue');
  await valuePanel.applyAttributeValueDialog(dlg);

  await expect(valuePanel.getTableRowContaining('Test Value')).toBeVisible();
});

test('Grouping Panel', async ({ standalone }) => {
  await new BackendApi(standalone).mockFilterGroups(groupId, groupIdTwo);
  const groups = new InstanceGroupsBrowserPage(standalone);
  await groups.goto();

  // just for testing panel fragment - should probably be moved somewhere?
  const grouping = new GroupSortCard(groups);
  await grouping.openGroupingPanel();
  await grouping.selectGrouping('This is a test attribute');

  await groups.screenshot('Doc_GroupingPanel');

  await grouping.closeGroupingPanel();
});

test('Product Upload', async ({ standalone }) => {
  await new BackendApi(standalone).mockFilterGroups(groupId);

  const instances = new InstancesBrowserPage(standalone, groupId);
  await instances.goto();
  await instances.screenshot('Doc_DemoInstancesEmpty');

  const products = new GroupsProductsPage(standalone, groupId);
  await products.goto();
  await products.screenshot('Doc_ProductsEmpty');

  const upload = await products.openUploadPanel();
  await upload.screenshot('Doc_ProductsUploadPanel');
  await upload.upload('test-product-2-direct.zip');

  const state = upload.getUploadState('test-product-2-direct');
  await expect(state).toContainText('Success');
  await products.screenshot('Doc_ProductsUploadSuccess');

  await products.getProductDetailsPanel('Demo Product');
  await products.screenshot('Doc_ProductDetailsPanel');
});

test('Create Instance Panel', async ({ standalone }) => {
  await new BackendApi(standalone).mockFilterGroups(groupId);

  const instances = new InstancesBrowserPage(standalone, groupId);
  await instances.goto();
  await instances.screenshot('Doc_DemoInstancesNoInstance');

  await instances.addInstance();
  await instances.screenshot('Doc_InstanceAdd');
});
