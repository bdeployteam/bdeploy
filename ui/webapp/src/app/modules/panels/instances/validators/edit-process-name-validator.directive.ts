import { Directive, Input, inject } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';
import { NodeType } from 'src/app/models/gen.dtos';
import {
  bdValidationIdExtractor,
  bdValidationRegisterMessageExtractor,
} from 'src/app/modules/core/validators/messages';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';

const ID = 'edit-process-name';
bdValidationRegisterMessageExtractor(bdValidationIdExtractor(ID));

@Directive({
    selector: '[appEditProcessNameValidator]',
    providers: [
        {
            provide: NG_VALIDATORS,
            useExisting: EditProcessNameValidatorDirective,
            multi: true,
        },
    ]
})
export class EditProcessNameValidatorDirective implements Validator {
  private readonly edit = inject(InstanceEditService);

  @Input() appEditProcessNameValidator: string;

  public validate(control: AbstractControl): ValidationErrors | null {
    const name = control.value as string;
    if (!name?.length) {
      return null; // "required" must be validated elsewhere.
    }

    const errors: ValidationErrors = {};
    for (const n of this.edit.state$.value.config.nodeDtos) {
      if (n.nodeConfiguration.nodeType === NodeType.CLIENT) {
        // it is OK for client applications!
        continue;
      }
      for (const a of n.nodeConfiguration.applications) {
        if (a.id === this.appEditProcessNameValidator) {
          continue; // skip self.
        }
        if (a.name.toLowerCase() === name.toLowerCase()) {
          errors[ID] = `Process name already in use`;
        }
      }
    }
    return errors;
  }
}
