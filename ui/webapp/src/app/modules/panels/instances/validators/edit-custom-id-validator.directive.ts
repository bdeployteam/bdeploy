import { Directive, inject } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';
import {
  BdValidationMessageExtractor,
  bdValidationRegisterMessageExtractor,
} from 'src/app/modules/core/validators/messages';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';

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
export class EditCustomIdValidatorDirective implements Validator, BdValidationMessageExtractor {
  private edit = inject(InstanceEditService);

  public readonly id = 'edit-custom-id';
  private readonly idRegExp = new RegExp(/^[A-Za-z][A-Za-z0-9_\\-\\.]*$/);

  constructor() {
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
    const ok = this.idRegExp.test(value);

    if (!ok) {
      errors[this.id] = 'Invalid ID Format';
    }

    return errors;
  }
}
