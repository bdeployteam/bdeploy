import { AbstractControl, ValidationErrors } from '@angular/forms';

export class ServerValidators {

  /** Validates: https://, /api, optional port, RFC 1123 compliant hostname */
  private static exp = new RegExp('^https://((?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)*[a-z0-9][a-z0-9-]{0,61}[a-z0-9])(:[0-9]+)?/api/?$', 'i');

  static serverApiUrl(control: AbstractControl): ValidationErrors | null {
    if (ServerValidators.exp.test(control.value)) {
      return null;
    } else {
      return { uri: 'URI does not match required pattern \'https://host[:port]/api\'' };
    }
  }

}
