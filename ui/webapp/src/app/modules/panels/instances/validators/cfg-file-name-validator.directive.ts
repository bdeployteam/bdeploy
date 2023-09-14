import { Directive, inject } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';
import {
  BdValidationMessageExtractor,
  bdValidationRegisterMessageExtractor,
} from 'src/app/modules/core/validators/messages';
import { ConfigFilesService } from '../services/config-files.service';

@Directive({
  selector: '[appCfgFileNameValidator]',
  providers: [
    {
      provide: NG_VALIDATORS,
      useExisting: CfgFileNameValidatorDirective,
      multi: true,
    },
  ],
})
export class CfgFileNameValidatorDirective implements Validator, BdValidationMessageExtractor {
  private cfgFiles = inject(ConfigFilesService);
  public readonly id = 'cfg-file-name';

  constructor() {
    bdValidationRegisterMessageExtractor(this);
  }

  public extract(label: string, errors: ValidationErrors): string {
    if (errors[this.id]) {
      return errors[this.id];
    }
  }

  public validate(control: AbstractControl): ValidationErrors | null {
    const name = control.value as string;
    if (!name?.length) {
      return null; // "required" must be validated elsewhere.
    }

    const errors = {};
    for (const f of this.cfgFiles.files$.value) {
      const p = this.cfgFiles.getPath(f);
      if (!!f.persistent && !f.persistent.instanceId) {
        continue; // file only in product, we can create/rename it.
      }
      if (p?.toLowerCase() === name.toLowerCase()) {
        errors[this.id] = `File name/path already in use`;
      }
    }
    return errors;
  }
}
