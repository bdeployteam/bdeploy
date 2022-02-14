import { Directive } from '@angular/core';
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
  selector: '[appEditCustomUidValidator]',
  providers: [
    {
      provide: NG_VALIDATORS,
      useExisting: EditCustomUidValidatorDirective,
      multi: true,
    },
  ],
})
export class EditCustomUidValidatorDirective
  implements Validator, BdValidationMessageExtractor
{
  public readonly id = 'edit-custom-uid';
  private readonly uidRegExp = new RegExp(/^[A-Za-z][A-Za-z0-9_\\-\\.]*$/);

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
    const ok = this.uidRegExp.test(value);

    if (!ok) {
      errors[this.id] = 'Invalid UID Format';
    }

    return errors;
  }
}
