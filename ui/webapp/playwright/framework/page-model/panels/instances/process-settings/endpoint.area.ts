import { BaseArea } from '@bdeploy-pom/base/base-area';
import { Locator, Page } from '@playwright/test';
import { FormCheckboxElement } from '@bdeploy-elements/form-checkbox.element';
import { ValueEditorElement } from '@bdeploy-elements/value-editor.element';

export class EndpointArea extends BaseArea {
  private readonly _area: Locator;

  constructor(page: Page, parent: Locator, private readonly id: string) {
    super(page);

    this._area = parent.getByTestId(id);
  }

  protected getArea(): Locator {
    return this._area;
  }

  getRawPath() {
    return this._area.getByTestId('raw-path');
  }

  getProcessedPath() {
    return this._area.getByTestId('processed-path');
  }

  getDisabledReason() {
    return this._area.getByTestId('disabled-reason');
  }

  getField(label: string) {
    return new ValueEditorElement(this.getArea(), label);
  }

  getCheckBox(label: string) {
    return new FormCheckboxElement(this.getArea(), label);
  }

}