import { Directive, Input, inject } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';
import { ManifestKey } from 'src/app/models/gen.dtos';
import {
  bdValidationIdExtractor,
  bdValidationRegisterMessageExtractor,
} from 'src/app/modules/core/validators/messages';
import { SystemsService } from '../../../primary/systems/services/systems.service';

const ID = 'system-on-server';
bdValidationRegisterMessageExtractor(bdValidationIdExtractor(ID));

@Directive({
    selector: '[appSystemOnServerValidator]',
    providers: [
        {
            provide: NG_VALIDATORS,
            useExisting: SystemOnServerValidatorDirective,
            multi: true,
        },
    ],
    standalone: false
})
export class SystemOnServerValidatorDirective implements Validator {
  private readonly systems = inject(SystemsService);

  @Input('appSystemOnServerValidator') serverName: string;

  public validate(control: AbstractControl): ValidationErrors | null {
    const value = control.value as ManifestKey;

    if (!this.serverName || !value) {
      return {}; // no server selected (YET), or no system selected
    }

    const system = this.systems.systems$.value?.find((s) => s.key.name === value?.name && s.key.tag === value?.tag);

    if (!system) {
      return {
        [ID]: `Cannot find system with key ${value?.name}:${value?.tag}`,
      };
    }

    if (system.minion !== this.serverName) {
      return {
        [ID]: `The selected system is on a different server than the selected target server.`,
      };
    }

    return {};
  }
}
