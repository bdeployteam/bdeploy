import { Directive } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';
import { ManagedMasterDto } from 'src/app/models/gen.dtos';
import { convert2String } from 'src/app/modules/core/utils/version.utils';
import {
  bdValidationIdExtractor,
  bdValidationRegisterMessageExtractor
} from 'src/app/modules/core/validators/messages';

const ID = 'variable-support-server';
bdValidationRegisterMessageExtractor(bdValidationIdExtractor(ID));

@Directive({
    selector: '[appVariableServerValidator]',
    providers: [
        {
            provide: NG_VALIDATORS,
            useExisting: VariableServerValidatorDirective,
            multi: true,
        },
    ]
})
export class VariableServerValidatorDirective implements Validator {
  validate(control: AbstractControl): ValidationErrors | null {
    const server = control.value as ManagedMasterDto;
    if (!server) {
      return null; // "required" must be validated elsewhere.
    }

    for (const k of Object.keys(server.nodes.nodes)) {
      const n = server.nodes.nodes[k].config;
      if (n.master) {
        const support =
          n.version.major === 0 || // dev version
          n.version.major > 4 || // 5.0+
          (n.version.major === 4 && n.version.minor >= 6); // 4.6+

        if (!support) {
          return {
            [ID]: `Server version ${convert2String(n.version)} does not support systems`,
          };
        }
      }
    }
    return {};
  }
}
