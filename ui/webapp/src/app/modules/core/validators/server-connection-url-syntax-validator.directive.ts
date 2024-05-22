import { Directive } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';

export const URL_VALIDATION = 'errorMsg';

@Directive({
  selector: '[appServerConnectionUrlSyntaxValid]',
  providers: [{ provide: NG_VALIDATORS, useExisting: ServerConnectionUrlSyntaxValidator, multi: true }],
})
export class ServerConnectionUrlSyntaxValidator implements Validator {
  private regex = new RegExp(/^(\S+:\/\/\S+:\d+($|\/\S+))?$/);

  public validate(control: AbstractControl): ValidationErrors {
    const value = control.value;
    if (!value) {
      return null;
    }
    return this.regex.test(control.value) ? null : { errorMsg: { value: value } };
  }
}
