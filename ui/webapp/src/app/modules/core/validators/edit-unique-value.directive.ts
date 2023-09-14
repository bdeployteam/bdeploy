import { Directive, Input } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';
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
export class EditUniqueValueValidatorDirective implements Validator, BdValidationMessageExtractor {
  public readonly id = 'edit-unique-value';

  @Input() disallowedValues: string[];
  @Input() disallowedMessage = 'ID is not unique';

  constructor(private edit: InstanceEditService) {
    bdValidationRegisterMessageExtractor(this);
  }

  public extract(label: string, errors: ValidationErrors): string {
    if (errors[this.id]) {
      return errors[this.id];
    }
  }

  public validate(control: AbstractControl): ValidationErrors | null {
    const value = control.value;
    const errors = {};
    const ok = !this.disallowedValues.includes(value);

    if (!ok) {
      errors[this.id] = this.disallowedMessage;
    }

    return errors;
  }
}
