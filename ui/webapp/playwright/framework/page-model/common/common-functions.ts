import { Locator, Page } from '@playwright/test';
import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { expect } from '@bdeploy-setup';
import { InstanceGroupsBrowserPage } from '@bdeploy-pom/primary/groups/groups-browser.page';

export async function createPanel<T extends BasePanel>(buttonHost: Locator, label: string, factory: (p: Page) => T): Promise<T> {
  await buttonHost.getByLabel(label).click();
  const panel = factory(buttonHost.page());
  await panel.expectOpen();
  return Promise.resolve(panel);
}

export async function createPanelFromRow<T extends BasePanel>(row: Locator, factory: (p: Page) => T): Promise<T> {
  await row.click();
  const panel = factory(row.page());
  await panel.expectOpen();
  return Promise.resolve(panel);
}

export async function waitForInstanceGroup(groups: InstanceGroupsBrowserPage, groupId: string) {
  // TODO: why? this should not be necessary, since the page should be updated by a websocket push. almost always that works, but *sometimes* it does not.
  try {
    await expect(groups.getTableRowContaining(groupId)).toBeVisible({ timeout: 1000 });
  } catch (e) {
    await groups.getDialog().page().reload();
    await expect(groups.getTableRowContaining(groupId)).toBeVisible();
  }
}