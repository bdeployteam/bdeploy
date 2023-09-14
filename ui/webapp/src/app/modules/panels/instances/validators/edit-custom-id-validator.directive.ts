import { Directive } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';
import {
  bdValidationIdExtractor,
  bdValidationRegisterMessageExtractor,
} from 'src/app/modules/core/validators/messages';

const ID = 'edit-custom-id';
bdValidationRegisterMessageExtractor(bdValidationIdExtractor(ID));

@Directive({
  selector: '[appEditCustomIdValidator]',
  providers: [
    {
      provide: NG_VALIDATORS,
      useExisting: EditCustomIdValidatorDirective,
      multi: true,
    },
  ],
})
export class EditCustomIdValidatorDirective implements Validator {
  private readonly idRegExp = new RegExp(/^[A-Za-z][A-Za-z0-9_\\-\\.]*$/);

  public validate(control: AbstractControl): ValidationErrors | null {
    const value = control.value;
    const errors = {};
    const ok = this.idRegExp.test(value);

    if (!ok) {
      errors[ID] = 'Invalid ID Format';
    }

    return errors;
  }
}
