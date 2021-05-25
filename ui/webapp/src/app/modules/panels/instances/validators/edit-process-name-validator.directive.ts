import { Directive } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';
import { BdValidationMessageExtractor, bdValidationRegisterMessageExtractor } from 'src/app/modules/core/validators/messages';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';

@Directive({
  selector: '[appEditProcessNameValidator]',
  providers: [{ provide: NG_VALIDATORS, useExisting: EditProcessNameValidatorDirective, multi: true }],
})
export class EditProcessNameValidatorDirective implements Validator, BdValidationMessageExtractor {
  public readonly id = 'edit-process-name';

  constructor(private edit: InstanceEditService) {
    bdValidationRegisterMessageExtractor(this);
  }

  extract(label: string, errors: ValidationErrors): string {
    if (!!errors[this.id]) {
      return errors[this.id];
    }
  }

  validate(control: AbstractControl): ValidationErrors | null {
    if (control.pristine) {
      return null; // only validate when *changing* then name, otherwise the name conflicts with itself.
    }

    const name = control.value as string;
    if (!name?.length) {
      return null; // "required" must be validated elsewhere.
    }

    const errors = {};
    for (const n of this.edit.state$.value.nodeDtos) {
      for (const a of n.nodeConfiguration.applications) {
        if (a.name.toLowerCase() === name.toLowerCase()) {
          errors[this.id] = `Process name already in use`;
        }
      }
    }
    return errors;
  }
}
