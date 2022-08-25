import { Directive } from '@angular/core';
import {
  AbstractControl,
  NG_VALIDATORS,
  ValidationErrors,
  Validator,
} from '@angular/forms';
import { ManagedMasterDto } from 'src/app/models/gen.dtos';
import { convert2String } from 'src/app/modules/core/utils/version.utils';
import {
  BdValidationMessageExtractor,
  bdValidationRegisterMessageExtractor,
} from 'src/app/modules/core/validators/messages';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';

@Directive({
  selector: '[appVariableServerValidator]',
  providers: [
    {
      provide: NG_VALIDATORS,
      useExisting: VariableServerValidatorDirective,
      multi: true,
    },
  ],
})
export class VariableServerValidatorDirective
  implements Validator, BdValidationMessageExtractor
{
  public readonly id = 'variable-support-server';

  constructor(private edit: InstanceEditService) {
    bdValidationRegisterMessageExtractor(this);
  }

  extract(label: string, errors: ValidationErrors): string {
    if (errors[this.id]) {
      return errors[this.id];
    }
  }

  validate(control: AbstractControl): ValidationErrors | null {
    const server = control.value as ManagedMasterDto;
    if (!server) {
      return null; // "required" must be validated elsewhere.
    }

    // eslint-disable-next-line no-unsafe-optional-chaining
    for (const k of Object.keys(server.minions.minions)) {
      const n = server.minions.minions[k];
      if (n.master) {
        const support =
          n.version.major === 0 || // dev version
          n.version.major > 4 || // 5.0+
          (n.version.major === 4 && n.version.minor >= 6); // 4.6+

        if (!support) {
          return {
            [this.id]: `Server version ${convert2String(
              n.version
            )} does not support systems`,
          };
        }
      }
    }
    return {};
  }
}
