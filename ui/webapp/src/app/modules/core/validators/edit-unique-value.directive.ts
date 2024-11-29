import { Directive, Input } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';
import {
  bdValidationIdExtractor,
  bdValidationRegisterMessageExtractor,
} from 'src/app/modules/core/validators/messages';

const ID = 'edit-unique-value';
bdValidationRegisterMessageExtractor(bdValidationIdExtractor(ID));

@Directive({
    selector: '[appEditUniqueValueValidator]',
    providers: [
        {
            provide: NG_VALIDATORS,
            useExisting: EditUniqueValueValidatorDirective,
            multi: true,
        },
    ],
    standalone: false
})
export class EditUniqueValueValidatorDirective implements Validator {
  @Input() disallowedValues: string[];
  @Input() disallowedMessage = 'ID is not unique';

  public validate(control: AbstractControl): ValidationErrors | null {
    const value = control.value;
    const errors = {};
    const ok = !this.disallowedValues.includes(value);

    if (!ok) {
      errors[ID] = this.disallowedMessage;
    }

    return errors;
  }
}
