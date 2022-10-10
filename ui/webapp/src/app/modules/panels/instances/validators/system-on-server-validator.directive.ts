import { Directive, Input } from '@angular/core';
import {
  AbstractControl,
  NG_VALIDATORS,
  ValidationErrors,
  Validator,
} from '@angular/forms';
import { ManifestKey } from 'src/app/models/gen.dtos';
import {
  BdValidationMessageExtractor,
  bdValidationRegisterMessageExtractor,
} from 'src/app/modules/core/validators/messages';
import { SystemsService } from '../../../primary/systems/services/systems.service';

@Directive({
  selector: '[appSystemOnServerValidator]',
  providers: [
    {
      provide: NG_VALIDATORS,
      useExisting: SystemOnServerValidatorDirective,
      multi: true,
    },
  ],
})
export class SystemOnServerValidatorDirective
  implements Validator, BdValidationMessageExtractor
{
  public readonly id = 'system-on-server';

  @Input('appSystemOnServerValidator') serverName: string;

  constructor(private systems: SystemsService) {
    bdValidationRegisterMessageExtractor(this);
  }

  extract(label: string, errors: ValidationErrors): string {
    if (errors[this.id]) {
      return errors[this.id];
    }
  }

  validate(control: AbstractControl): ValidationErrors | null {
    const value = control.value as ManifestKey;

    if (!this.serverName || !value) {
      return {}; // no server selected (YET), or no system selected
    }

    const system = this.systems.systems$.value?.find(
      (s) => s.key.name === value?.name && s.key.tag === value?.tag
    );

    if (!system) {
      return {
        [this.id]: `Cannot find system with key ${value?.name}:${value?.tag}`,
      };
    }

    if (system.minion !== this.serverName) {
      return {
        [this
          .id]: `The selected system is on a different server than the selected target server.`,
      };
    }

    return {};
  }
}
