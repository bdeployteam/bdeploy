import { Directive, Input } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';
import {
  bdValidationIdExtractor,
  bdValidationRegisterMessageExtractor,
} from 'src/app/modules/core/validators/messages';

const ID = 'edit-item-in-list';
bdValidationRegisterMessageExtractor(bdValidationIdExtractor(ID));

@Directive({
    selector: '[appEditItemInListValidator]',
    providers: [
        {
            provide: NG_VALIDATORS,
            useExisting: EditItemInListValidatorDirective,
            multi: true,
        },
    ],
    standalone: false
})
export class EditItemInListValidatorDirective implements Validator {
  @Input() allowedValues: string[];

  public validate(control: AbstractControl): ValidationErrors | null {
    if (!control.value?.length) {
      return {};
    }

    const value = control.value.split(',').map((v) => v.trim());
    const errors = {};

    for (const v of value) {
      const ok = this.allowedValues.includes(v);
      if (!ok) {
        errors[ID] = `${v} is not a valid entry`;
      }
    }

    return errors;
  }
}
