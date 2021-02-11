import { AbstractControl, FormGroup, ValidationErrors } from '@angular/forms';

export class ParameterValidators {
  private static uidRegExp = new RegExp('^[A-Za-z][A-Za-z0-9_\\-\\.]*$');

  /** Validates that the UID pattern contains only allowed characters */
  static uidPattern(control: AbstractControl): ValidationErrors | null {
    const value = control.value;
    const ok = ParameterValidators.uidRegExp.test(value);
    return ok ? null : { uidPattern: { value: value } };
  }

  /** Validates that the given control holds a numeric value */
  static numeric(control: AbstractControl): ValidationErrors | null {
    // Browser returns an empty string if the users enters a non-numeric value
    if (control.value === '') {
      return { numeric: { value: control.value } };
    }
    return null;
  }

  /** Validates that the given value is unique */
  static unique(getUsedValues: (validated: AbstractControl) => string[]) {
    return (control: AbstractControl) => {
      const defined = getUsedValues(control);
      if (defined.includes(control.value)) {
        return { unique: { value: control.value } };
      }
      return null;
    };
  }

  /** this control value must be equal to given control's value */
  static notEqualValueValidator(targetKey: string, toMatchKey: string) {
    return (group: FormGroup): { [key: string]: any } => {
      const target = group.controls[targetKey];
      const toMatch = group.controls[toMatchKey];
      if (!target || !toMatch) {
        return null;
      }
      const isDifferent = target.value !== toMatch.value;

      // set equal value error on dirty controls
      if (!isDifferent && target.valid && toMatch.valid) {
        toMatch.setErrors({ notEqualValue: targetKey });
        const message = targetKey + ' != ' + toMatchKey;
        return { notEqualValue: message };
      }
      if (isDifferent && toMatch.hasError('notEqualValue')) {
        toMatch.setErrors(null);
      }
      return null;
    };
  }
}
