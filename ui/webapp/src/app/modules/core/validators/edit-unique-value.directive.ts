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
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';

@Directive({
  selector: '[appEditUniqueValueValidator]',
  providers: [
    {
      provide: NG_VALIDATORS,
      useExisting: EditUniqueValueValidatorDirective,
      multi: true,
    },
  ],
})
export class EditUniqueValueValidatorDirective
  implements Validator, BdValidationMessageExtractor
{
  public readonly id = 'edit-unique-value';

  @Input() disallowedValues: string[];

  constructor(private edit: InstanceEditService) {
    bdValidationRegisterMessageExtractor(this);
  }

  extract(label: string, errors: ValidationErrors): string {
    if (errors[this.id]) {
      return errors[this.id];
    }
  }

  validate(control: AbstractControl): ValidationErrors | null {
    const value = control.value;
    const errors = {};
    const ok = !this.disallowedValues.includes(value);

    if (!ok) {
      errors[this.id] = 'UID is not unique';
    }

    return errors;
  }
}
