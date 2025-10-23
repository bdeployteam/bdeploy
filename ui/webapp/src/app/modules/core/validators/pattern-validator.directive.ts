import { Directive, Input } from '@angular/core';
import { AbstractControl, NG_VALIDATORS } from '@angular/forms';
import {
  bdValidationIdExtractor,
  bdValidationRegisterMessageExtractor,
  StrictValidationErrors,
  StrictValidator,
  ValidatorConfiguration,
} from './messages';

const ID = 'regex_w_msg';
bdValidationRegisterMessageExtractor(bdValidationIdExtractor(ID));

export type Pattern = 'LDAP' | 'IMAP' | 'SMTP';

export interface PatternValidatorConfiguration extends ValidatorConfiguration {
  regex?: string;
  type?: Pattern;
}

interface KnownPatternConfig {
  errorMessage?: string;
  regex: RegExp;
}

const KNOWN_PATTERNS: Record<Pattern, KnownPatternConfig> = {
  ['IMAP']: {
    regex: new RegExp('^[i|I][m|M][a|A][p|P][s|S]?:\\/\\/[\\w\\-\\.~]+:\\d{1,5}($|(\\/[\\w\\-\\.~]+)+$)'),
    errorMessage: "Doesn't match IMAP URL scheme",
  },
  ['SMTP']: {
    regex: new RegExp('^[s|S][m|M][t|T][p|P][s|S]?:\\/\\/[\\w\\-\\.~]+:\\d{1,5}$'),
    errorMessage: "Doesn't match SMTP URL scheme",
  },
  ['LDAP']: {
    regex: new RegExp('^[l|L][d|D][a|A][p|P][s|S]?:\\/\\/[\\w\\-\\.~]+:\\d{1,5}$'),
    errorMessage: "Doesn't match LDAP URL scheme",
  },
};

/**
 * This directive should receive as input an object of type RegexValidatorConfiguration
 * which configures the mode of usage of the validator.
 *
 * If type is supplied, then the known regex for that type will be used. For known types the
 * error message is also preset and cannot be modified.
 * Otherwise, you need to configure a regex and an errorMessage.
 */
@Directive({
  selector: '[appPattern]',
  providers: [{ provide: NG_VALIDATORS, useExisting: PatternValidator, multi: true }]
})
export class PatternValidator implements StrictValidator {
  @Input() appPattern: PatternValidatorConfiguration | Pattern;

  public validate(control: AbstractControl): StrictValidationErrors {
    const value = control.value;
    if (!value) {
      return null;
    }

    const regex = this.determineRegex();
    if (!value || regex.test(control.value)) {
      return null;
    } else {
      const errors: StrictValidationErrors = {};
      errors[ID] = this.getErrorMessageIfConfigured() ?? `Value must match: ${regex.source}`;

      return errors;
    }
  }

  private getErrorMessageIfConfigured() {
    if (typeof this.appPattern === 'string') {
      return KNOWN_PATTERNS[this.appPattern].errorMessage;
    }
    return this.appPattern.errorMessage;
  }

  private determineRegex() {
    if (typeof this.appPattern === 'string') {
      return KNOWN_PATTERNS[this.appPattern].regex;
    }

    return new RegExp(this.appPattern.regex);
  }
}
