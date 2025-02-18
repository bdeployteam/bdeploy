import { BaseArea } from '@bdeploy-pom/base/base-area';
import { Locator } from '@playwright/test';
import { createPanel } from '@bdeploy-pom/common/common-functions';
import {
  EditProcessControlGroupPanel
} from '@bdeploy-pom/panels/instances/process-settings/edit-process-control-group.panel';

export class ProcessControlGroupArea extends BaseArea {
  constructor(private readonly _parent: Locator, private readonly _name: string) {
    super(_parent.page());
  }

  protected getArea(): Locator {
    return this._parent.locator('app-control-group', { hasText: this._name });
  }

  async getEditPanel() {
    return createPanel(this.getArea(), 'Edit Control Group', p => new EditProcessControlGroupPanel(p));
  }
}