import { Directive } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';

export const GROUP_ID_VALIDATION = 'groupId';
const GROUP_ID_REGEX = new RegExp('^[A-Za-z0-9][A-Za-z0-9_\\-\\.]*$');

@Directive({
  selector: '[appGroupId]',
  providers: [{ provide: NG_VALIDATORS, useExisting: GroupIdValidator, multi: true }],
})
export class GroupIdValidator implements Validator {
  validate(control: AbstractControl): ValidationErrors {
    const value = control.value;
    const ok = GROUP_ID_REGEX.test(value);
    return ok ? null : { groupId: { value: value } };
  }
}
