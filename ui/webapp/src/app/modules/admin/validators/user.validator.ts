import { AbstractControl, ValidationErrors } from '@angular/forms';

export class UserValidators {
  private static nameRegExp = new RegExp('^[A-Za-z0-9][A-Za-z0-9_\\-\\.]*$');

  static namePattern(control: AbstractControl): ValidationErrors | null {
    const value = control.value;
    const ok = UserValidators.nameRegExp.test(value);
    return ok ? null : { namePattern: { value: value } };
  }
}
