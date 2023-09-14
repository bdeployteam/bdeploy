import { Directive, Input } from '@angular/core';
import { NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';
import {
  BdValidationMessageExtractor,
  bdValidationRegisterMessageExtractor,
} from 'src/app/modules/core/validators/messages';

@Directive({
  selector: '[appPropagateErrorValidator]',
  providers: [
    {
      provide: NG_VALIDATORS,
      useExisting: PropagateErrorValidatorDirective,
      multi: true,
    },
  ],
})
export class PropagateErrorValidatorDirective implements Validator, BdValidationMessageExtractor {
  public readonly id = 'propagate-error-input';

  @Input('appPropagateErrorValidator') error: string;

  constructor() {
    bdValidationRegisterMessageExtractor(this);
  }

  public extract(label: string, errors: ValidationErrors): string {
    if (errors[this.id]) {
      return errors[this.id];
    }
  }

  public validate(): ValidationErrors | null {
    if (this.error) {
      return { [this.id]: this.error };
    }
    return null;
  }
}
