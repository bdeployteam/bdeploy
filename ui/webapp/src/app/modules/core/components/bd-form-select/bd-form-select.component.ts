import { ChangeDetectionStrategy, Component, Input, TemplateRef, Type, ViewEncapsulation, inject } from '@angular/core';
import { ControlValueAccessor, NgControl, UntypedFormControl, FormsModule } from '@angular/forms';
import { ErrorStateMatcher, MatOption } from '@angular/material/core';
import { bdValidationMessage } from '../../validators/messages';
import { MatFormField, MatLabel, MatPrefix, MatError } from '@angular/material/form-field';
import { MatSelect } from '@angular/material/select';
import {
  BdFormSelectComponentOptionComponent,
  ComponentWithSelectedOption
} from '../bd-form-select-component-option/bd-form-select-component-option.component';
import { NgTemplateOutlet } from '@angular/common';

@Component({
    selector: 'app-bd-form-select',
    templateUrl: './bd-form-select.component.html',
    encapsulation: ViewEncapsulation.None,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [MatFormField, MatLabel, MatSelect, FormsModule, MatOption, BdFormSelectComponentOptionComponent, MatPrefix, NgTemplateOutlet, MatError]
})
export class BdFormSelectComponent<T> implements ControlValueAccessor, ErrorStateMatcher {
  protected readonly ngControl = inject(NgControl, { self: true, optional: true });

  @Input() label: string;
  @Input() name: string;
  @Input() values: T[] = [];
  @Input() labels: string[];
  @Input() required: boolean | string;
  @Input() disabled: boolean | string;
  @Input() allowNone = false;
  @Input() errorDisplay: 'touched' | 'immediate' = 'touched';
  @Input() component: Type<ComponentWithSelectedOption<T>>;
  @Input() prefix: TemplateRef<T>;

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

  constructor() {
    if (this.ngControl) {
      this.ngControl.valueAccessor = this;
    }
  }

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

  isErrorState(control: UntypedFormControl | null): boolean {
    if (!this.isInvalid()) {
      return false;
    }

    return this.errorDisplay === 'immediate' || !!(control && (control.dirty || control.touched));
  }

  private isInvalid() {
    if (!this.ngControl) {
      return false;
    }

    return this.ngControl.invalid;
  }

  public getErrorMessage() {
    if (!this.ngControl) {
      return null;
    }

    return bdValidationMessage(this.label, this.ngControl.errors);
  }
}
