import { Directive, Input } from '@angular/core';
import { NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';
import {
  bdValidationIdExtractor,
  bdValidationRegisterMessageExtractor,
} from 'src/app/modules/core/validators/messages';

const ID = 'propagate-error-input';
bdValidationRegisterMessageExtractor(bdValidationIdExtractor(ID));

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
export class PropagateErrorValidatorDirective implements Validator {
  @Input('appPropagateErrorValidator') error: string;

  public validate(): ValidationErrors | null {
    if (this.error) {
      return { [ID]: this.error };
    }
    return null;
  }
}
