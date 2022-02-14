import { Directive, Input } from '@angular/core';
import {
  AbstractControl,
  NG_VALIDATORS,
  ValidationErrors,
  Validator,
} from '@angular/forms';

export const PASSWORD_VALIDATION = 'passwordMismatch';

@Directive({
  selector: '[appPasswordVerification]',
  providers: [
    {
      provide: NG_VALIDATORS,
      useExisting: PasswordVerificationValidator,
      multi: true,
    },
  ],
})
export class PasswordVerificationValidator implements Validator {
  @Input('appPasswordVerification') referencePass: string;

  validate(control: AbstractControl): ValidationErrors | null {
    const value = control.value;
    const ok = value === this.referencePass;
    return ok ? null : { passwordMismatch: { value: true } };
  }
}
