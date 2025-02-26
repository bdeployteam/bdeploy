import { TestInfo } from '@playwright/test';
import { expect, test } from '@bdeploy-setup';
import { ReposBrowserPage } from '@bdeploy-pom/primary/repos/repos-browser.page';
import { ReposSoftwarePage } from '@bdeploy-pom/primary/repos/repos-software.page';
import { createInstanceGroup } from '@bdeploy-pom/common/common-tasks';
import { GroupsProductsPage } from '@bdeploy-pom/primary/groups/groups-products.page';
import { BackendApi } from '@bdeploy-backend';

function repoId(testInfo: TestInfo) {
  return `Repo-${testInfo.workerIndex}`;
}

function groupId(testInfo: TestInfo) {
  return `RepoGroup-${testInfo.workerIndex}`;
}

test.beforeEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.deleteRepo(repoId(testInfo));
  await api.deleteGroup(groupId(testInfo));
})

test.afterEach(async ({ standalone }, testInfo) => {
  const api = new BackendApi(standalone);
  await api.deleteRepo(repoId(testInfo));
  await api.deleteGroup(groupId(testInfo));
});

test('Software Repositories', async ({ standalone }, testInfo) => {
  const browser = new ReposBrowserPage(standalone);
  await browser.goto();

  const addPanel = await browser.getAddRepoPanel();
  await addPanel.fill(repoId(testInfo), 'Test Repository');
  await addPanel.save();

  await expect(browser.getTableRowContaining(repoId(testInfo))).toBeVisible();

  const sw = new ReposSoftwarePage(standalone, repoId(testInfo));
  await sw.goto();

  const upload = await sw.openUploadPanel();
  await upload.upload('test-product-2-direct.zip');

  const state = upload.getUploadState('test-product-2-direct');
  await expect(state).toContainText('Success');

  await expect(sw.getSoftwareRow('io.bdeploy/demo/product')).toBeVisible();

  await createInstanceGroup(standalone, groupId(testInfo));
  const groupProd = new GroupsProductsPage(standalone, groupId(testInfo));
  await groupProd.goto();

  const impPanel = await groupProd.openProductImportPanel();
  await impPanel.screenshot('Doc_ImportProduct_SelectRepo');
  await impPanel.selectRepo(repoId(testInfo));
  await impPanel.screenshot('Doc_ImportProduct_SelectProduct');
  await impPanel.selectProduct('Demo Product'); // not using the ID but the user-friendly name.
  await impPanel.selectVersion('2.0.0');
  await impPanel.screenshot('Doc_ImportProduct_SelectVersion');
  await impPanel.transfer();

  await expect(groupProd.getProductRow('2.0.0')).toBeVisible();
  await groupProd.screenshot('Doc_ImportProduct_Success');
});