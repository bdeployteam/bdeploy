import { expect, Locator, Page } from '@playwright/test';
import { BaseDialog } from '@bdeploy-pom/base/base-dialog';

export class LoginPage extends BaseDialog {
  private readonly _username: Locator;
  private readonly _password: Locator;
  private readonly _login: Locator;

  constructor(page: Page) {
    super(page, 'app-login');
    this._username = page.getByLabel('Username');
    this._password = page.getByLabel('Password');
    this._login = page.getByRole('button', { name: 'Login' });
  }

  async goto() {
    // in case we're logged in already -> logout
    await this.page.context().clearCookies();

    // go to '/' which would redirect to the login, and after login return to the groups browser.
    await this.page.goto('/');

    await this.page.waitForURL('**/#/login**');

    await expect(this._username).toBeVisible();
    await expect(this._password).toBeVisible();
    await expect(this._login).toBeVisible();
    await expect(this._login).toBeDisabled();
  }

  async login(user: string, pw: string) {
    await this._username.click();
    await this._username.fill(user);
    await this._password.click();
    await this._password.fill(pw);

    await expect(this._login).toBeEnabled();
    await this._login.click();

    await this.page.waitForURL('**/#/groups/browser');
  }
}
