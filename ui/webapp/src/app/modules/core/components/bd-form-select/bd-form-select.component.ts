import { ChangeDetectionStrategy, Component, Input, TemplateRef, Type, ViewEncapsulation, inject } from '@angular/core';
import { ControlValueAccessor, NgControl, UntypedFormControl } from '@angular/forms';
import { ErrorStateMatcher } from '@angular/material/core';
import { bdValidationMessage } from '../../validators/messages';

@Component({
  selector: 'app-bd-form-select',
  templateUrl: './bd-form-select.component.html',
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BdFormSelectComponent implements ControlValueAccessor, ErrorStateMatcher {
  protected readonly ngControl = inject(NgControl, { self: true, optional: true });

  @Input() label: string;
  @Input() name: string;
  @Input() values: unknown[] = [];
  @Input() labels: string[];
  @Input() required: any;
  @Input() disabled: any;
  @Input() allowNone = false;
  @Input() errorDisplay: 'touched' | 'immediate' = 'touched';
  @Input() component: Type<unknown>;
  @Input() prefix: TemplateRef<unknown>;

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

  private internalValue: any = null;
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

  writeValue(v: unknown): void {
    if (v !== this.internalValue) {
      this.internalValue = v;
    }
  }

  registerOnChange(fn: any): void {
    this.onChangedCb = fn;
  }

  registerOnTouched(fn: any): void {
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
