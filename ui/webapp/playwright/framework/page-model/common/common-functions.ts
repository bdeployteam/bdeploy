import { Locator, Page } from '@playwright/test';
import { BasePanel } from '@bdeploy-pom/base/base-panel';

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