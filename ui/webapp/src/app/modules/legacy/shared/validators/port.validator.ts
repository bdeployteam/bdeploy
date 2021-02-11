import { Directive } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, Validator } from '@angular/forms';

@Directive({
  selector: '[appValidPort]',
  providers: [{ provide: NG_VALIDATORS, useExisting: PortValidatorDirective, multi: true }],
})
export class PortValidatorDirective implements Validator {
  validate(control: AbstractControl): { [key: string]: any } | null {
    const num = Number(control.value);

    if (Number.isNaN(num)) {
      return { port: 'Given port is not a number' };
    }

    if (num < 0 || num > 65535) {
      return { port: 'Given port is out of range - the valid range is from 0 to 65535' };
    }
  }
}
