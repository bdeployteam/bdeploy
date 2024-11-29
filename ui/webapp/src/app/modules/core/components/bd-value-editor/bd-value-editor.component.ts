import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnInit,
  Output,
  TemplateRef,
  ViewChild,
  inject,
} from '@angular/core';
import { ControlContainer, ControlValueAccessor, NgControl, NgForm } from '@angular/forms';
import { ErrorStateMatcher } from '@angular/material/core';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, Subject, debounceTime } from 'rxjs';
import {
  ApplicationConfiguration,
  ApplicationDto,
  CustomEditor,
  InstanceConfigurationDto,
  LinkedValueConfiguration,
  ManifestKey,
  SystemConfiguration,
  VariableType,
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
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: false
})
export class BdValueEditorComponent implements OnInit, ControlValueAccessor, ErrorStateMatcher {
  protected readonly ngControl = inject(NgControl, { self: true, optional: true });

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
  private _type: VariableType;
  @Input() set type(v: VariableType) {
    this._type = v;
    if (this.isBoolean() && !this.isLink() && !this.internalValue.value) {
      this.internalValue.value = 'false';
    }
  }
  get type(): VariableType {
    return this._type;
  }
  @Input() customEditor: string;
  @Input() product: ManifestKey;
  @Input() group: string;

  @Input() completions: ContentCompletion[];
  @Input() completionPrefixes: ContentCompletion[];

  @Input() actions: TemplateRef<unknown>;

  @Output() customEditorLoaded = new EventEmitter<CustomEditor>();

  @ViewChild('linkEditor', { static: false }) linkEditor: BdFormInputComponent;
  @ViewChild('valueEditor', { static: false })
  valueEditor: BdFormInputComponent;

  private readonly modelChanged = new Subject<LinkedValueConfiguration>();

  protected passwordLock = true;
  protected linkEditorPopup$ = new BehaviorSubject<BdPopupDirective>(null);
  protected booleanValue;
  protected preview;

  protected get value(): LinkedValueConfiguration {
    return this.internalValue;
  }
  protected set value(v: LinkedValueConfiguration) {
    if (v !== this.internalValue) {
      this.writeValue(v);
      this.fireChange(v);
    }
  }

  protected internalValue: LinkedValueConfiguration = {
    value: null,
    linkExpression: null,
  };
  private onTouchedCb: () => void = () => {
    /* intentionally empty */
  };
  private onChangedCb: (_: unknown) => void = () => {
    /* intentionally empty */
  };

  constructor() {
    if (this.ngControl) {
      this.ngControl.valueAccessor = this;
    }
  }

  ngOnInit(): void {
    this.modelChanged.pipe(debounceTime(50)).subscribe((v) => {
      this.updatePreview(v);
    });

    // init with lock in case of password - which is the default to prevent timing issues, reset for all others.
    if (this.type !== VariableType.PASSWORD || this.customEditor) {
      this.passwordLock = false;
    }
  }

  onBlur() {
    this.onTouchedCb();
  }

  writeValue(v: any): void {
    if (v !== this.internalValue) {
      this.internalValue = v;
      this.booleanValue = this.internalValue?.value === 'true';
      this.modelChanged.next(v);
    }
  }

  registerOnChange(fn: any): void {
    this.onChangedCb = fn;
  }

  registerOnTouched(fn: any): void {
    this.onTouchedCb = fn;
  }

  onFocus() {
    this.updatePreview(this.internalValue);
  }

  private fireChange(value: LinkedValueConfiguration) {
    this.onChangedCb(value);
  }

  private updatePreview(value: LinkedValueConfiguration) {
    if (value?.linkExpression?.length) {
      this.preview = getRenderPreview(this.internalValue, this.process, this.instance, this.system);
    } else {
      this.preview = null;
    }
  }

  isErrorState(): boolean {
    if (!this.isInvalid()) {
      return false;
    }

    return true; // immediate always
  }

  getTransitiveErrorMessage() {
    if (this.linkEditor?.isInvalid()) {
      return this.linkEditor.getErrorMessage();
    }

    if (this.valueEditor?.isInvalid()) {
      return this.valueEditor.getErrorMessage();
    }

    return 'Test'; // should be null.
  }

  private isInvalid() {
    return this.ngControl?.invalid;
  }

  protected getErrorMessage() {
    if (!this.ngControl) {
      return null;
    }

    return bdValidationMessage(this.label, this.ngControl.errors);
  }

  protected isBoolean() {
    return this.type === VariableType.BOOLEAN;
  }

  protected isLink() {
    if (!this.internalValue) {
      return false;
    }
    return this.internalValue.linkExpression !== null && this.internalValue.linkExpression !== undefined;
  }

  protected isPort() {
    return this.type === VariableType.CLIENT_PORT || this.type === VariableType.SERVER_PORT;
  }

  protected getInputType() {
    if (!this.type) {
      return undefined;
    }
    switch (this.type) {
      case VariableType.CLIENT_PORT:
      case VariableType.SERVER_PORT:
      case VariableType.NUMERIC:
        return 'number';
      case VariableType.PASSWORD:
        return 'password';
    }
  }

  protected doRevert() {
    if (!this.defaultValue) {
      this.writeValue({
        value: this.isBoolean() ? 'false' : '',
        linkExpression: null,
      });
    } else {
      this.writeValue(cloneDeep(this.defaultValue));
    }

    this.fireChange(this.internalValue);
  }

  protected doChangeValue(event: unknown) {
    let reset = false;
    const val = `${event}`; // convert to string in case of number, etc.

    // check type;
    switch (this.type) {
      case VariableType.BOOLEAN:
        if (val !== 'true' && val !== 'false') {
          console.log(`Value is a boolean, but the value is not true or false, resetting to default.`);
          reset = true;
        }
        break;
      case VariableType.NUMERIC:
      case VariableType.CLIENT_PORT:
      case VariableType.SERVER_PORT:
        if (isNaN(Number(val))) {
          console.log(`Value is not a number: ${val}, resetting to default.`);
          reset = true;
        }
        break;
    }

    if (reset) {
      // we can only revert to the default value if it is a plain value here - otherwise we would "snap" back
      // to link expression mode in case we're switching mode on number or boolean with an invalid value currently
      // in the input field.
      if (this.defaultValue?.value && !this.defaultValue?.linkExpression) {
        this.doRevert();
      } else {
        // in case the default value is a link expression, we must resort to the empty value.
        this.writeValue({ value: '', linkExpression: null });
        this.fireChange(this.internalValue);
      }
    } else {
      this.writeValue({ value: val, linkExpression: null });
      this.fireChange(this.internalValue);
    }
  }

  protected doChangeLink(val: string) {
    this.writeValue({ value: null, linkExpression: val || '' });
    this.fireChange(this.internalValue);
  }

  /* template */ doChangeBooleanValue() {
    this.doChangeValue(this.booleanValue ? 'true' : 'false');
  }

  protected appendLink(v: string) {
    this.doChangeLink(this.internalValue?.linkExpression + v);
  }

  protected makeValueLink() {
    if (this.type === VariableType.PASSWORD) {
      this.doChangeLink(''); // DON'T ever apply a password to the plain text editor, rather clear it.
    }
    this.doChangeLink(this.internalValue?.value);
  }

  protected makeValuePlain() {
    if (this.type === VariableType.PASSWORD) {
      this.doChangeValue('');
      return;
    }

    if (this.value?.linkExpression?.indexOf('{{') >= 0) {
      this.doChangeValue(getRenderPreview(this.internalValue, this.process, this.instance, this.system));
    } else {
      this.doChangeValue(this.internalValue?.linkExpression);
    }
  }
}
