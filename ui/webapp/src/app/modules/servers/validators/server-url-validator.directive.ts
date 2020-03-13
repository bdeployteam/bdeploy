import { Directive } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';

@Directive({
  selector: '[appServerUrlValidator]',
  providers: [{provide: NG_VALIDATORS, useExisting: ServerUrlValidatorDirective, multi: true}]
})
export class ServerUrlValidatorDirective implements Validator {

  /** Validates: https://, /api, optional port, RFC 1123 compliant hostname */
  private exp = new RegExp('^https://((?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)*[a-z0-9][a-z0-9-]{0,61}[a-z0-9])(:[0-9]+)?/api$', 'i');

  validate(control: AbstractControl): ValidationErrors | null {
    if (this.exp.test(control.value)) {
      return null;
    } else {
      return { uri: 'URI does not match required pattern \'https://host[:port]/api\'' };
    }

  }

}
