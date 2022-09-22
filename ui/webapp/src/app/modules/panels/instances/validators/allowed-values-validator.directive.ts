import { Directive, Input } from '@angular/core';
import {
  AbstractControl,
  NG_VALIDATORS,
  ValidationErrors,
  Validator,
} from '@angular/forms';
import {
  BdValidationMessageExtractor,
  bdValidationRegisterMessageExtractor,
} from 'src/app/modules/core/validators/messages';

@Directive({
  selector: '[appAllowedValuesValidator]',
  providers: [
    {
      provide: NG_VALIDATORS,
      useExisting: AllowedValuesValidatorDirective,
      multi: true,
    },
  ],
})
export class AllowedValuesValidatorDirective
  implements Validator, BdValidationMessageExtractor
{
  public readonly id = 'edit-allowed-values';

  @Input() appAllowedValuesValidator: string[];

  constructor() {
    bdValidationRegisterMessageExtractor(this);
  }

  extract(label: string, errors: ValidationErrors): string {
    if (errors[this.id]) {
      return errors[this.id];
    }
  }

  validate(control: AbstractControl): ValidationErrors | null {
    const value = control.value?.value; // LinkedValue
    const errors = {};

    // we don't currently evaluate link expression...
    if (!value?.length) {
      return errors;
    }

    if (this.appAllowedValuesValidator.findIndex((v) => v === value) === -1) {
      errors[
        this.id
      ] = `Value must be one of: ${this.appAllowedValuesValidator.join(', ')}`;
    }

    return errors;
  }
}
