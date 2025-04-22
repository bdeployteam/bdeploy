import { Page } from '@playwright/test';
import { BasePanel } from '@bdeploy-pom/base/base-panel';
import { FormSelectElement } from '@bdeploy-elements/form-select.elements';
import { FormCheckboxElement } from '@bdeploy-elements/form-checkbox.element';

export class BaseConfigurationPanel extends BasePanel {
  constructor(page: Page) {
    super(page, 'app-edit-config');
  }

  public getName() {
    return this.getDialog().getByLabel('Name');
  }

  public getDescription() {
    return this.getDialog().getByLabel('Description');
  }

  public getPurpose() {
    return new FormSelectElement(this.getDialog(), 'Purpose');
  }

  public getAutomaticStartup() {
    return new FormCheckboxElement(this.getDialog(), 'Automatic Startup');
  }

  public getAutomaticUninstall() {
    return new FormCheckboxElement(this.getDialog(), 'Automatic Uninstall');
  }

  public getProduct() {
    return new FormSelectElement(this.getDialog(), 'Product');
  }

  public getProductVersion() {
    return new FormSelectElement(this.getDialog(), 'Product Version');
  }

  public getVersionRegex() {
    return this.getDialog().getByLabel('Product Version Regular Expression');
  }

  public getManagedServer() {
    return new FormSelectElement(this.getDialog(), 'Managed Server');
  }

}
