import { Directive, Input } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';
import { CLIENT_NODE_NAME } from 'src/app/models/consts';
import { BdValidationMessageExtractor, bdValidationRegisterMessageExtractor } from 'src/app/modules/core/validators/messages';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';

@Directive({
  selector: '[appEditProcessNameValidator]',
  providers: [{ provide: NG_VALIDATORS, useExisting: EditProcessNameValidatorDirective, multi: true }],
})
export class EditProcessNameValidatorDirective implements Validator, BdValidationMessageExtractor {
  public readonly id = 'edit-process-name';

  @Input() appEditProcessNameValidator: string;

  constructor(private edit: InstanceEditService) {
    bdValidationRegisterMessageExtractor(this);
  }

  extract(label: string, errors: ValidationErrors): string {
    if (!!errors[this.id]) {
      return errors[this.id];
    }
  }

  validate(control: AbstractControl): ValidationErrors | null {
    const name = control.value as string;
    if (!name?.length) {
      return null; // "required" must be validated elsewhere.
    }

    const errors = {};
    for (const n of this.edit.state$.value?.config.nodeDtos) {
      if (n.nodeName === CLIENT_NODE_NAME) {
        // it is OK for client applications!
        continue;
      }
      for (const a of n.nodeConfiguration.applications) {
        if (a.uid === this.appEditProcessNameValidator) {
          continue; // skip self.
        }
        if (a.name.toLowerCase() === name.toLowerCase()) {
          errors[this.id] = `Process name already in use`;
        }
      }
    }
    return errors;
  }
}
