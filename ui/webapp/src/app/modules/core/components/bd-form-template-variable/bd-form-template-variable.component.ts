import { ChangeDetectionStrategy, Component, inject, Input, ViewEncapsulation } from '@angular/core';
import { ControlValueAccessor, NgControl, FormsModule } from '@angular/forms';
import { TemplateVariable, TemplateVariableType } from 'src/app/models/gen.dtos';
import { BdFormToggleComponent } from '../bd-form-toggle/bd-form-toggle.component';
import { MatTooltip } from '@angular/material/tooltip';
import { BdFormInputComponent } from '../bd-form-input/bd-form-input.component';

@Component({
    selector: 'app-bd-form-template-variable',
    templateUrl: './bd-form-template-variable.component.html',
    encapsulation: ViewEncapsulation.None,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [BdFormToggleComponent, FormsModule, MatTooltip, BdFormInputComponent]
})
export class BdFormTemplateVariableComponent<T> implements ControlValueAccessor {
  protected readonly ngControl = inject(NgControl, { self: true, optional: true });
  protected readonly TemplateVariableType = TemplateVariableType;

  @Input() templateVariable: TemplateVariable;

  constructor() {
    if (this.ngControl) {
      this.ngControl.valueAccessor = this;
    }
  }

  get type(): string {
    switch (this.templateVariable.type) {
      case TemplateVariableType.NUMERIC:
        return 'number';
      case TemplateVariableType.PASSWORD:
        return 'password';
      default:
        return 'text';
    }
  }

  public get value() {
    return this.internalValue;
  }
  public set value(v) {
    if (v !== this.internalValue) {
      this.internalValue = v;
      this.onTouchedCb();
      this.onChangedCb(v);
    }
  }

  private internalValue: T = null;
  private onTouchedCb: () => void = () => {
    /* intentionally empty */
  };
  private onChangedCb: (_: unknown) => void = () => {
    /* intentionally empty */
  };

  writeValue(v: T): void {
    if (v !== this.internalValue) {
      this.internalValue = v;
    }
  }

  registerOnChange(fn: (_: unknown) => void): void {
    this.onChangedCb = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouchedCb = fn;
  }
}
