import { AbstractControl, ValidationErrors } from '@angular/forms';
import { trim } from 'lodash';

export class InstanceValidators {

  private static urlRegExp = new RegExp('^https:\\/\\/..*:[0-9]+\\/api$');

  static urlPattern(control: AbstractControl): ValidationErrors | null {
    const value = trim(control.value);
    const ok = InstanceValidators.urlRegExp.test(value);
    return ok ? null : {'urlPattern' : {value: value}};
  }

}
