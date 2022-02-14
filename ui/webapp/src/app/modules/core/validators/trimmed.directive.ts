import { Directive } from '@angular/core';
import {
  AbstractControl,
  NG_VALIDATORS,
  ValidationErrors,
  Validator,
} from '@angular/forms';

export const TRIM_VALIDATION = 'trim';

@Directive({
  selector: '[appTrimmed]',
  providers: [
    { provide: NG_VALIDATORS, useExisting: TrimmedValidator, multi: true },
  ],
})
export class TrimmedValidator implements Validator {
  validate(control: AbstractControl): ValidationErrors {
    const value = control.value;
    const ok = String(value).trim() === String(value);
    return ok ? null : { trim: { value: value } };
  }
}
