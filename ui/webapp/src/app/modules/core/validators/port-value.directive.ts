import { Directive, Input } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';

export const PORT_VALIDATION = 'port-value';

@Directive({
  selector: '[appPortValueValidator]',
  providers: [
    {
      provide: NG_VALIDATORS,
      useExisting: PortValueValidatorDirective,
      multi: true,
    },
  ],
})
export class PortValueValidatorDirective implements Validator {
  @Input() appPortValueValidator: boolean;

  public validate(control: AbstractControl): ValidationErrors | null {
    if (!this.appPortValueValidator) {
      return {};
    }

    const value = control.value as number;
    const errors = {};
    const ok = !(value < 0 || value > 64535);

    if (!ok) {
      errors[PORT_VALIDATION] = { value: value };
    }

    return errors;
  }
}
