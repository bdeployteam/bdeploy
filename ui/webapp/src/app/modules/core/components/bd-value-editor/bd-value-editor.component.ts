import {
  Component,
  EventEmitter,
  Input,
  OnInit,
  Optional,
  Output,
  Self,
  TemplateRef,
  ViewChild,
} from '@angular/core';
import {
  ControlContainer,
  ControlValueAccessor,
  NgControl,
  NgForm,
} from '@angular/forms';
import { ErrorStateMatcher } from '@angular/material/core';
import { BehaviorSubject } from 'rxjs';
import {
  ApplicationConfiguration,
  ApplicationDto,
  CustomEditor,
  InstanceConfigurationDto,
  LinkedValueConfiguration,
  ManifestKey,
  ParameterType,
  SystemConfiguration,
} from 'src/app/models/gen.dtos';
import { getRenderPreview } from '../../utils/linked-values.utils';
import { bdValidationMessage } from '../../validators/messages';
import { ContentCompletion } from '../bd-content-assist-menu/bd-content-assist-menu.component';
import { BdFormInputComponent } from '../bd-form-input/bd-form-input.component';
import { BdPopupDirective } from '../bd-popup/bd-popup.directive';

@Component({
  selector: 'app-bd-value-editor',
  templateUrl: './bd-value-editor.component.html',
  styleUrls: ['./bd-value-editor.component.css'],
  viewProviders: [{ provide: ControlContainer, useExisting: NgForm }],
})
export class BdValueEditorComponent
  implements OnInit, ControlValueAccessor, ErrorStateMatcher
{
  @Input() label: string;
  @Input() name: string;
  @Input() required: boolean;
  @Input() suggested: string[];
  @Input() process: ApplicationConfiguration;
  @Input() instance: InstanceConfigurationDto;
  @Input() system: SystemConfiguration;
  @Input() applications: ApplicationDto[];
  @Input() disabled: boolean;
  @Input() editorDisabled: boolean;
  @Input() defaultValue: LinkedValueConfiguration;
  @Input() type: ParameterType;
  @Input() customEditor: string;
  @Input() product: ManifestKey;
  @Input() group: string;

  @Input() completions: ContentCompletion[];
  @Input() completionPrefixes: ContentCompletion[];

  @Input() actions: TemplateRef<any>;

  @Output() customEditorLoaded = new EventEmitter<CustomEditor>();

  @ViewChild('linkEditor', { static: false }) linkEditor: BdFormInputComponent;
  @ViewChild('valueEditor', { static: false })
  valueEditor: BdFormInputComponent;

  /* template */ passwordLock = true;
  /* template */ linkEditorPopup$ = new BehaviorSubject<BdPopupDirective>(null);
  /* template */ booleanValue;

  /* template */ get value(): LinkedValueConfiguration {
    return this.internalValue;
  }
  /* template */ set value(v: LinkedValueConfiguration) {
    if (v !== this.internalValue) {
      this.writeValue(v);
      this.onChangedCb(v);
    }
  }

  /* template */ internalValue: LinkedValueConfiguration = {
    value: null,
    linkExpression: null,
  };
  private onTouchedCb: () => void = () => {
    /* intentionally empty */
  };
  private onChangedCb: (_: any) => void = () => {
    /* intentionally empty */
  };

  constructor(@Optional() @Self() public ngControl: NgControl) {
    if (ngControl) {
      ngControl.valueAccessor = this;
    }
  }

  ngOnInit(): void {
    if (this.isBoolean()) {
      this.booleanValue = this.internalValue?.value === 'true';
    }

    // init with lock in case of password - which is the default to prevent timing issues, reset for all others.
    if (this.type !== ParameterType.PASSWORD || this.customEditor) {
      this.passwordLock = false;
    }
  }

  onBlur() {
    this.onTouchedCb();
  }

  writeValue(v: any): void {
    if (v !== this.internalValue) {
      this.internalValue = v;
    }
  }

  registerOnChange(fn: any): void {
    this.onChangedCb = fn;
  }

  registerOnTouched(fn: any): void {
    this.onTouchedCb = fn;
  }

  isErrorState(): boolean {
    if (!this.isInvalid()) {
      return false;
    }

    return true; // immediate always
  }

  getTransitiveErrorMessage() {
    if (this.linkEditor && this.linkEditor.isInvalid()) {
      return this.linkEditor.getErrorMessage();
    }

    if (this.valueEditor && this.valueEditor.isInvalid()) {
      return this.valueEditor.getErrorMessage();
    }

    return 'Test'; // should be null.
  }

  private isInvalid() {
    return this.ngControl && this.ngControl.invalid;
  }

  /* template */ getErrorMessage() {
    if (!this.ngControl) {
      return null;
    }

    return bdValidationMessage(this.label, this.ngControl.errors);
  }

  /* template */ isBoolean() {
    return this.type === ParameterType.BOOLEAN;
  }

  /* template */ isLink() {
    if (!this.internalValue) {
      return false;
    }
    return (
      this.internalValue.linkExpression !== null &&
      this.internalValue.linkExpression !== undefined
    );
  }

  /* template */ isPort() {
    return (
      this.type === ParameterType.CLIENT_PORT ||
      this.type === ParameterType.SERVER_PORT
    );
  }

  /* template */ getInputType() {
    if (!this.type) {
      return undefined;
    }
    switch (this.type) {
      case ParameterType.CLIENT_PORT:
      case ParameterType.SERVER_PORT:
      case ParameterType.NUMERIC:
        return 'number';
      case ParameterType.PASSWORD:
        return 'password';
    }
  }

  /* template */ doRevert() {
    if (!this.defaultValue) {
      this.internalValue = {
        value: this.isBoolean() ? 'false' : '',
        linkExpression: null,
      };
    } else {
      this.internalValue = this.defaultValue;
    }

    if (this.isBoolean()) {
      this.booleanValue = this.internalValue.value === 'true';
    }

    this.onChangedCb(this.internalValue);
  }

  /* template */ doChangeValue(event: any) {
    let reset = false;
    const val = `${event}`; // convert to string in case of number, etc.

    // check type;
    switch (this.type) {
      case ParameterType.BOOLEAN:
        if (val !== 'true' && val !== 'false') {
          console.log(
            `Value is a boolean, but the value is not true or false, resetting to default.`
          );
          reset = true;
        }
        break;
      case ParameterType.NUMERIC:
      case ParameterType.CLIENT_PORT:
      case ParameterType.SERVER_PORT:
        if (isNaN(Number(val))) {
          console.log(`Value is not a number: ${val}, resetting to default.`);
          reset = true;
        }
        break;
    }

    if (reset) {
      this.doRevert();
    } else {
      this.internalValue = { value: val, linkExpression: null };
      this.onChangedCb(this.internalValue);
    }
  }

  /* template */ doChangeLink(val: string) {
    this.internalValue = { value: null, linkExpression: val ? val : '' };
    this.onChangedCb(this.internalValue);
  }

  /* tempalte */ doChangeBooleanValue() {
    this.doChangeValue(this.booleanValue ? 'true' : 'false');
  }

  /* template */ appendLink(v: string) {
    this.doChangeLink(this.internalValue?.linkExpression + v);
  }

  /* template */ makeValueLink() {
    if (this.type === ParameterType.PASSWORD) {
      this.doChangeLink(''); // DON'T ever apply a password to the plain text editor, rather clear it.
    }
    this.doChangeLink(this.internalValue?.value);
  }

  /* template */ makeValuePlain() {
    if (this.type === ParameterType.PASSWORD) {
      this.doChangeValue('');
      return;
    }

    if (this.value?.linkExpression?.indexOf('{{') >= 0) {
      this.doChangeValue(
        getRenderPreview(
          this.internalValue,
          this.process,
          this.instance,
          this.system
        )
      );
    } else {
      this.doChangeValue(this.internalValue?.linkExpression);
    }
  }
}
