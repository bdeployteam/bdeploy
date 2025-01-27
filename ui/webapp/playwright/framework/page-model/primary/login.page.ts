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
    try {
      await this.page.goto('/', { waitUntil: 'commit' });
    } catch (error) {
      // when running on firefox, this is unfortunately "expected" as firefox does
      // not handle the "duplicate" navigation gracefully (the redirect will abort the
      // first navigation ungracefully). The actual failure code is unfortunately also
      // depending on timing it seems, can be NS_BINDING_ABORTED or NS_ERROR_FAILURE
      if (this.page.context().browser().browserType().name() !== 'firefox') {
        throw error;
      }
    }
    await this.page.waitForURL('**/#/login**', { waitUntil: 'load' });

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
