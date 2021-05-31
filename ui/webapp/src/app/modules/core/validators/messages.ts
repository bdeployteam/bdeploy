import { ValidationErrors } from '@angular/forms';
import { GROUP_ID_VALIDATION } from './group-id';
import { PASSWORD_VALIDATION } from './password-verification';

export interface BdValidationMessageExtractor {
  id: string;

  extract(label: string, errors: ValidationErrors): string;
}

const msgExtractors: BdValidationMessageExtractor[] = [];
export function bdValidationRegisterMessageExtractor(extractor: BdValidationMessageExtractor) {
  if (!!msgExtractors.find((e) => e.id === extractor.id)) {
    return;
  }

  console.log(`Register message extractor: ${extractor.id}`);

  msgExtractors.push(extractor);
}

/**
 * Returns proper error messages for all common validators which can be registered on a bd-form-*
 */
export function bdValidationMessage(label: string, errors: ValidationErrors): string {
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

  for (const x of msgExtractors) {
    const r = x.extract(label, errors);
    if (r !== undefined) {
      return r;
    }
  }
}
