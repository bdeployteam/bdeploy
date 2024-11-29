import { Directive } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';

export const ID_VALIDATION = 'identifier';
const ID_REGEX = /^[A-Za-z0-9][A-Za-z0-9_.-]*$/;

@Directive({
    selector: '[appIdentifier]',
    providers: [{ provide: NG_VALIDATORS, useExisting: IdentifierValidator, multi: true }],
    standalone: false
})
export class IdentifierValidator implements Validator {
  public validate(control: AbstractControl): ValidationErrors {
    const value = control.value;
    const ok = ID_REGEX.test(value);
    return ok ? null : { identifier: { value: value } };
  }
}
