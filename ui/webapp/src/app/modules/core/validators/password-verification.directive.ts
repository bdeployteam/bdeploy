import { Directive, Input } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';

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
  standalone: false,
})
export class PasswordVerificationValidator implements Validator {
  private validateCb: () => void = () => {};

  private _referencePass: string;
  @Input('appPasswordVerification')
  set referencePass(v: string) {
    this._referencePass = v;
    this.validateCb();
  }
  get referencePass(): string {
    return this._referencePass;
  }

  public validate(control: AbstractControl): ValidationErrors | null {
    const value = control.value;
    const ok = value === this.referencePass;
    return ok ? null : { passwordMismatch: { value: true } };
  }

  public registerOnValidatorChange(fn: () => void): void {
    this.validateCb = fn;
  }
}
