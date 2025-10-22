import { BaseArea } from '@bdeploy-pom/base/base-area';
import { expect, Locator } from '@playwright/test';

export class InstallationConfirmationPopup extends BaseArea {
  constructor(private readonly _parent: Locator) {
    super(_parent.page());
  }

  protected getArea(): Locator {
    return this._parent.locator('app-bd-dialog-message').locator('app-bd-notification-card', { hasText: ' Running Applications' });
  }

  async shouldNotBeVisible() {
    await expect(this.getArea()).toHaveCount(0);
  }

  async shouldContainExactlyTheseProcesses(processDetailTexts: string[]): Promise<void> {
    await expect(this.getArea().locator("li")).toHaveCount(processDetailTexts.length);

    const texts = await this.getArea().locator("li").allTextContents();
    expect(texts).toEqual(expect.arrayContaining(processDetailTexts));
  }

  async ok() {
    await this.getArea().getByRole('button', { name: 'Stop applications and continue' }).click();
  }

  async cancel() {
    await this.getArea().getByRole('button', { name: 'Cancel activation' }).click();
  }
}