import { ValidationErrors } from '@angular/forms';
import { ID_VALIDATION } from './identifier.directive';
import { PASSWORD_VALIDATION } from './password-verification.directive';
import { PORT_VALIDATION } from './port-value.directive';
import { URL_VALIDATION } from './server-connection-url-syntax-validator.directive';
import { TRIM_VALIDATION } from './trimmed.directive';
import { VARIABLE_REGEX_VALIDATION } from './variable-regex-validator.directive';

export type BdValidationMessageExtractor = (label: string, errors: ValidationErrors) => string;

const msgExtractors: BdValidationMessageExtractor[] = [];

export function bdValidationRegisterMessageExtractor(extractor: BdValidationMessageExtractor) {
  msgExtractors.push(extractor);
}

export function bdValidationIdExtractor(id: string): BdValidationMessageExtractor {
  return (_, errors) => {
    if (errors[id]) {
      return errors[id];
    }
  };
}

/**
 * Returns proper error messages for all common validators which can be registered on a bd-form-*
 */
export function bdValidationMessage(label: string, errors: ValidationErrors): string {
  if (!errors) {
    return null;
  }

  // transitive = passing through from another control;
  if (errors['transitive']) {
    return errors['transitive'];
  }

  // commonly available validators
  if (errors['required']) {
    return `${label} is required.`;
  }
  if (errors['minlength']) {
    return `${label} must be at least ${errors['minlength'].requiredLength} characters.`;
  }
  if (errors['maxlength']) {
    return `${label} must be at maximum ${errors['maxlength'].requiredLength} characters.`;
  }

  // our own validators
  if (errors[ID_VALIDATION]) {
    return `${label} contains invalid characters`;
  }

  if (errors[TRIM_VALIDATION]) {
    return `${label} contains leading or trailing spaces`;
  }

  if (errors[URL_VALIDATION]) {
    return `${label} has an invalid syntax`;
  }

  if (errors[PASSWORD_VALIDATION]) {
    return `${label} must match the given password`;
  }

  if (errors[PORT_VALIDATION]) {
    return `${label} is out of range: ${errors[PORT_VALIDATION].value}`;
  }

  if (errors[VARIABLE_REGEX_VALIDATION]) {
    return `${label} value does not match regex: ${errors[VARIABLE_REGEX_VALIDATION].value}`;
  }

  for (const x of msgExtractors) {
    const r = x(label, errors);
    if (r !== undefined) {
      return r;
    }
  }

  return 'Unknown validation error';
}
