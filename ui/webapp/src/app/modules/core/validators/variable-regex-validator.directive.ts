import { Directive, Input } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';
import { ApplicationConfiguration, InstanceConfigurationDto, SystemConfiguration } from 'src/app/models/gen.dtos';
import { createLinkedValue, getRenderPreview } from '../utils/linked-values.utils';

export const VARIABLE_REGEX_VALIDATION = 'variableRegex';

export interface VariableRegexValidationContext {
  regex: string;
  process: ApplicationConfiguration;
  instance: InstanceConfigurationDto;
  system: SystemConfiguration;
}

@Directive({
  selector: '[appVariableRegexValidator]',
  providers: [{ provide: NG_VALIDATORS, useExisting: VariableRegexValidator, multi: true }],
})
export class VariableRegexValidator implements Validator {
  @Input('appVariableRegexValidator') context: VariableRegexValidationContext;

  public validate(control: AbstractControl): ValidationErrors | null {
    if (!this.context || !this.context.regex) {
      return null;
    }
    const preview = getRenderPreview(
      createLinkedValue(String(control.value)),
      this.context.process,
      this.context.instance,
      this.context.system,
    );
    return this.checkIfMatches(preview) ? null : { [VARIABLE_REGEX_VALIDATION]: { value: this.context.regex } };
  }

  private checkIfMatches(preview: string): boolean {
    try {
      const regex = new RegExp(this.context.regex);
      return regex.test(preview);
    } catch (error) {
      console.error('Invalid regular expression:', this.context.regex, error);
      return false; // Return false if the regex is invalid
    }
  }
}
