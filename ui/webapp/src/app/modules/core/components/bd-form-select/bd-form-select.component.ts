import {
  ChangeDetectionStrategy,
  Component,
  Input,
  Optional,
  Self,
  TemplateRef,
  Type,
  ViewEncapsulation,
} from '@angular/core';
import {
  ControlValueAccessor,
  NgControl,
  UntypedFormControl,
} from '@angular/forms';
import { ErrorStateMatcher } from '@angular/material/core';
import { bdValidationMessage } from '../../validators/messages';

@Component({
  selector: 'app-bd-form-select',
  templateUrl: './bd-form-select.component.html',
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BdFormSelectComponent
  implements ControlValueAccessor, ErrorStateMatcher
{
  @Input() label: string;
  @Input() name: string;
  @Input() values: any[] = [];
  @Input() labels: string[];
  @Input() required: any;
  @Input() disabled: any;
  @Input() allowNone = false;
  @Input() errorDisplay: 'touched' | 'immediate' = 'touched';
  @Input() component: Type<any>;
  @Input() prefix: TemplateRef<any>;

  /* template */ get value() {
    return this.internalValue;
  }
  /* template */ set value(v) {
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
  private onChangedCb: (_: any) => void = () => {
    /* intentionally empty */
  };

  constructor(@Optional() @Self() public ngControl: NgControl) {
    if (ngControl) {
      ngControl.valueAccessor = this;
    }
  }

  writeValue(v: any): void {
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

    return (
      this.errorDisplay === 'immediate' ||
      !!(control && (control.dirty || control.touched))
    );
  }

  private isInvalid() {
    if (!this.ngControl) {
      return false;
    }

    return this.ngControl.invalid;
  }

  /* template */ getErrorMessage() {
    if (!this.ngControl) {
      return null;
    }

    return bdValidationMessage(this.label, this.ngControl.errors);
  }
}
