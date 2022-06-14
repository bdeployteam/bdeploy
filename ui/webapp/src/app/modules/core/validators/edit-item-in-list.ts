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
  selector: '[appEditItemInListValidator]',
  providers: [
    {
      provide: NG_VALIDATORS,
      useExisting: EditItemInListValidatorDirective,
      multi: true,
    },
  ],
})
export class EditItemInListValidatorDirective
  implements Validator, BdValidationMessageExtractor
{
  public readonly id = 'edit-item-in-list';

  @Input() allowedValues: string[];

  constructor() {
    bdValidationRegisterMessageExtractor(this);
  }

  extract(label: string, errors: ValidationErrors): string {
    if (errors[this.id]) {
      return errors[this.id];
    }
  }

  validate(control: AbstractControl): ValidationErrors | null {
    if (!control.value?.length) {
      return {};
    }

    const value = control.value.split(',').map((v) => v.trim());
    const errors = {};

    for (const v of value) {
      const ok = this.allowedValues.includes(v);
      if (!ok) {
        errors[this.id] = `${v} is not a valid entry`;
      }
    }

    return errors;
  }
}
