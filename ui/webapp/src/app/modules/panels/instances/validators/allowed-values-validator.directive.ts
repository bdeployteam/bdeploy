import { Directive, Input } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';
import {
  bdValidationIdExtractor,
  bdValidationRegisterMessageExtractor,
} from 'src/app/modules/core/validators/messages';

const ID = 'edit-allowed-values';
bdValidationRegisterMessageExtractor(bdValidationIdExtractor(ID));

@Directive({
  selector: '[appAllowedValuesValidator]',
  providers: [
    {
      provide: NG_VALIDATORS,
      useExisting: AllowedValuesValidatorDirective,
      multi: true,
    },
  ]
})
export class AllowedValuesValidatorDirective implements Validator {
  @Input() appAllowedValuesValidator: string[];

  public validate(control: AbstractControl): ValidationErrors | null {
    const value = control.value?.value; // LinkedValue
    const errors: ValidationErrors = {};

    // we don't currently evaluate link expression...
    if (!value?.length) {
      return errors;
    }

    if (this.appAllowedValuesValidator.indexOf(value) === -1) {
      errors[ID] = `Value must be one of: ${this.appAllowedValuesValidator.join(', ')}`;
    }

    return errors;
  }
}
