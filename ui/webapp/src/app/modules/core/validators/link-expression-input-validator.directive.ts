import { Directive, Input } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';
import {
  ApplicationConfiguration,
  InstanceConfigurationDto,
  SystemConfiguration,
  VariableType,
} from 'src/app/models/gen.dtos';
import { createLinkedValue, getRenderPreview } from 'src/app/modules/core/utils/linked-values.utils';
import {
  bdValidationIdExtractor,
  bdValidationRegisterMessageExtractor,
} from 'src/app/modules/core/validators/messages';

const ID = 'link-expression-input';
bdValidationRegisterMessageExtractor(bdValidationIdExtractor(ID));

@Directive({
    selector: '[appLinkExpressionInputValidator]',
    providers: [
        {
            provide: NG_VALIDATORS,
            useExisting: LinkExpressionInputValidatorDirective,
            multi: true,
        },
    ],
    standalone: false
})
export class LinkExpressionInputValidatorDirective implements Validator {
  @Input('appLinkExpressionInputValidator') isLink: boolean;
  @Input('appLinkExpressionInputValidatorProcess') process: ApplicationConfiguration;
  @Input('appLinkExpressionInputValidatorInstance') instance: InstanceConfigurationDto;
  @Input('appLinkExpressionInputValidatorSystem') system: SystemConfiguration;
  @Input('appLinkExpressionInputValidatorType') type: VariableType;

  public validate(control: AbstractControl): ValidationErrors | null {
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

  private validateLink(value: string): ValidationErrors | null {
    const errors = {};

    if (value?.indexOf('{{') < 0) {
      errors[ID] = "Link expression should contain link specifiers '{{'.";
      return errors;
    }

    if ((value?.match(/{{/g) || []).length !== (value?.match(/}}/g) || []).length) {
      errors[ID] = "Link expression should contain equal number of '{{' and '}}'.";
      return errors;
    }

    const expanded = getRenderPreview(createLinkedValue(value), this.process, this.instance, this.system);

    const unresolved = expanded.match(/{{([^}]+)}}/g);
    if (unresolved) {
      errors[ID] = `Link expression contains unresolvable expressions: ${unresolved.join(', ')}`;
      return errors;
    }

    const type = this.type || VariableType.STRING;
    switch (type) {
      case VariableType.BOOLEAN:
        if (expanded?.toLowerCase() !== 'true' && expanded?.toLowerCase() !== 'false') {
          errors[ID] = 'Link expression does not expand to a boolean value.';
          return errors;
        }
        break;
      case VariableType.NUMERIC:
      case VariableType.CLIENT_PORT:
      case VariableType.SERVER_PORT:
        if (isNaN(Number(expanded))) {
          errors[ID] = 'Link expression does not expand to a numeric value.';
          return errors;
        }
        break;
    }

    return errors;
  }

  private validatePlain(value: string): ValidationErrors | null {
    const errors = {};

    if (value?.indexOf('{{') > 0) {
      errors[ID] = "Plain value should not contain link specifiers '{{'.";
      return errors;
    }

    return errors;
  }
}
