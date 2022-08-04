import { Directive, Input } from '@angular/core';
import {
  AbstractControl,
  NG_VALIDATORS,
  ValidationErrors,
  Validator,
} from '@angular/forms';
import {
  ApplicationConfiguration,
  InstanceConfigurationDto,
  ParameterType,
  SystemConfiguration,
} from 'src/app/models/gen.dtos';
import {
  createLinkedValue,
  getRenderPreview,
} from 'src/app/modules/core/utils/linked-values.utils';
import {
  BdValidationMessageExtractor,
  bdValidationRegisterMessageExtractor,
} from 'src/app/modules/core/validators/messages';

@Directive({
  selector: '[appLinkExpressionInputValidator]',
  providers: [
    {
      provide: NG_VALIDATORS,
      useExisting: LinkExpressionInputValidatorDirective,
      multi: true,
    },
  ],
})
export class LinkExpressionInputValidatorDirective
  implements Validator, BdValidationMessageExtractor
{
  public readonly id = 'link-expression-input';

  @Input('appLinkExpressionInputValidator') isLink: boolean;

  @Input('appLinkExpressionInputValidatorProcess')
  process: ApplicationConfiguration;
  @Input('appLinkExpressionInputValidatorInstance')
  instance: InstanceConfigurationDto;
  @Input('appLinkExpressionInputValidatorSystem') system: SystemConfiguration;
  @Input('appLinkExpressionInputValidatorType') type: ParameterType;

  constructor() {
    bdValidationRegisterMessageExtractor(this);
  }

  extract(label: string, errors: ValidationErrors): string {
    if (errors[this.id]) {
      return errors[this.id];
    }
  }

  validate(control: AbstractControl): ValidationErrors | null {
    const value = control.value as string;
    if (!value?.length) {
      return null; // "required" must be validated elsewhere.
    }

    if (this.isLink) {
      return this.validateLink(value);
    } else {
      return this.validatePlain(value);
    }
  }

  validateLink(value: string): ValidationErrors | null {
    const errors = {};

    if (value?.indexOf('{{') < 0) {
      errors[this.id] = "Link expression should contain link specifiers '{{'.";
      return errors;
    }

    if (
      (value?.match(/{{/g) || []).length !== (value?.match(/}}/g) || []).length
    ) {
      errors[this.id] =
        "Link expression should contain equal number of '{{' and '}}'.";
      return errors;
    }

    const expanded = getRenderPreview(
      createLinkedValue(value),
      this.process,
      this.instance,
      this.system
    );

    const unresolved = expanded.match(/{{([^}]+)}}/g);
    if (unresolved) {
      errors[
        this.id
      ] = `Link expression contains unresolvable expressions: ${unresolved.join(
        ', '
      )}`;
      return errors;
    }

    const type = this.type || ParameterType.STRING;
    switch (type) {
      case ParameterType.BOOLEAN:
        if (
          expanded?.toLowerCase() !== 'true' &&
          expanded?.toLowerCase() !== 'false'
        ) {
          errors[this.id] =
            'Link expression does not expand to a boolean value.';
          return errors;
        }
        break;
      case ParameterType.NUMERIC:
      case ParameterType.CLIENT_PORT:
      case ParameterType.SERVER_PORT:
        if (isNaN(Number(expanded))) {
          errors[this.id] =
            'Link expression does not expand to a numeric value.';
          return errors;
        }
        break;
    }

    return errors;
  }

  validatePlain(value: string): ValidationErrors | null {
    const errors = {};

    if (value?.indexOf('{{') > 0) {
      errors[this.id] = "Plain value should not contain link specifiers '{{'.";
      return errors;
    }

    return errors;
  }
}
