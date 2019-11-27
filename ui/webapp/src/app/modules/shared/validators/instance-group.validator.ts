import { AbstractControl, ValidationErrors } from '@angular/forms';

export class InstanceGroupValidators {

  private static nameRegExp = new RegExp('^[A-Za-z0-9][A-Za-z0-9_\\-\\.]*$');

  static namePattern(control: AbstractControl): ValidationErrors | null {
    const value = control.value;
    const ok = InstanceGroupValidators.nameRegExp.test(value);
    return ok ? null : {'namePattern' : {value: value}};
  }

}
