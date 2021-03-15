import { ValidationErrors } from '@angular/forms';
import { GROUP_ID_VALIDATION } from './group-id';
import { PASSWORD_VALIDATION } from './password-verification';

/**
 * Returns proper error messages for all common validators which can be registered on a bd-form-*
 */
export function bdValidationMessage(label: string, errors: ValidationErrors) {
  if (!errors) {
    return null;
  }

  // commonly available validators
  if (!!errors['required']) {
    return `${label} is required.`;
  }
  if (!!errors['minlength']) {
    return `${label} must be at least ${errors['minlength'].requiredLength} characters.`;
  }
  if (!!errors['maxlength']) {
    return `${label} must be at maximum ${errors['maxlength'].requiredLength} characters.`;
  }

  // our own validators
  if (!!errors[GROUP_ID_VALIDATION]) {
    return `${label} contains invalid characters`;
  }

  if (!!errors[PASSWORD_VALIDATION]) {
    return `${label} must match the given password`;
  }
}
