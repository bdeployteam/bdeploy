import { InstanceGroupsBrowserPage } from '@bdeploy-pom/primary/groups/groups-browser.page';
import { expect } from '@bdeploy-setup';
import { Page } from '@playwright/test';
import { GroupsProductsPage } from '@bdeploy-pom/primary/groups/groups-products.page';
import { InstancePurpose } from '@bdeploy/models/gen.dtos';
import { InstancesBrowserPage } from '@bdeploy-pom/primary/instances/instances-browser.page';
import { BackendApi } from '@bdeploy-backend';
import { waitForInstanceGroup } from '@bdeploy-pom/common/common-functions';

export async function createInstanceGroup(page: Page, groupId: string, image: string = null) {
  const groups = new InstanceGroupsBrowserPage(page);
  await groups.goto();

  const panel = await groups.addInstanceGroup();
  await expect(panel.getTitle()).toContainText('Add Instance Group');

  const logoSelector = panel.getDialog().getByAltText('logo');
  await expect(logoSelector).toHaveAttribute('src', '/assets/no-image.svg');

  await panel.fill(groupId, groupId, 'This is a demo instance group', true, image);

  if (image) {
    await expect(logoSelector).toHaveAttribute('src', /data:image.*/);
  }

  const saveRq = new BackendApi(page).waitForGroupPut();
  await panel.save();

  // wait for the panel to be gone.
  await expect(panel.getDialog()).not.toBeAttached();
  await saveRq;

  await waitForInstanceGroup(groups, groupId);
}

export async function uploadProduct(page: Page, groupId: string, product: string) {
  const products = new GroupsProductsPage(page, groupId);
  await products.goto();

  const upload = await products.openUploadPanel();
  await upload.upload(product + '.zip');

  const state = upload.getUploadState(product);
  await expect(state).toContainText('Success');
}

export async function createInstance(page: Page, groupId: string, name: string, description: string, purpose: InstancePurpose,
                                     productName: string, productVersion: string, managedServer: string = null, versionRegex: string = null, autoStart = false,
                                     autoUninstall = true, system: string = null) {
  const instances = new InstancesBrowserPage(page, groupId);
  await instances.goto();

  const panel = await instances.addInstance();
  const id = await panel.getInstanceId();
  await panel.fill(name, description, purpose, productName, productVersion, managedServer, versionRegex, autoStart, autoUninstall, system);

  await panel.save();
  await expect(panel.getDialog()).not.toBeAttached();

  return id;
}