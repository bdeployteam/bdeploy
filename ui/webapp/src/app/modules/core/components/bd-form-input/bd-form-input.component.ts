import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  Output,
  TemplateRef,
  ViewChild,
  ViewEncapsulation,
  inject,
} from '@angular/core';
import { ControlValueAccessor, NgControl, UntypedFormControl } from '@angular/forms';
import { MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { ErrorStateMatcher } from '@angular/material/core';
import { BehaviorSubject } from 'rxjs';
import { bdValidationMessage } from '../../validators/messages';
import { ContentCompletion } from '../bd-content-assist-menu/bd-content-assist-menu.component';

@Component({
  selector: 'app-bd-form-input',
  templateUrl: './bd-form-input.component.html',
  styleUrls: ['./bd-form-input.component.css'],
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BdFormInputComponent implements ControlValueAccessor, ErrorStateMatcher {
  protected ngControl = inject(NgControl, { self: true, optional: true });
  protected elementRef = inject(ElementRef);

  @Input() label: string;
  @Input() name: string;
  @Input() required: any;
  @Input() disabled: any;
  @Input() type: string;
  @Input() placeholder: string;
  @Input() suggested: string[];
  @Input() errorDisplay: 'touched' | 'immediate' = 'touched';
  @Input() passwordLock = false;
  @Input() prefix: TemplateRef<any>;
  @Input() assistValues: ContentCompletion[];
  @Input() assistPrefixes: ContentCompletion[];
  @Input() errorFallback: string;
  @Input() statusMessage: string;
  @Input() passwordShowable = false;

  // eslint-disable-next-line @angular-eslint/no-output-native
  @Output() focus = new EventEmitter<any>();

  @ViewChild(MatAutocompleteTrigger) private trigger: MatAutocompleteTrigger;

  protected showPassword = false;
  protected filteredSuggested$ = new BehaviorSubject<string[]>([]);

  accessor autoFilled = false;

  public get value() {
    return this.internalValue;
  }
  public set value(v) {
    if (v !== this.internalValue) {
      this.writeValue(v);
      this.onChangedCb(v);
    }
  }

  private internalValue: any = '';
  private onTouchedCb: () => void = () => {
    /* intentionally empty */
  };
  private onChangedCb: (_: any) => void = () => {
    /* intentionally empty */
  };

  constructor() {
    if (this.ngControl) {
      this.ngControl.valueAccessor = this;
    }
    this.updateFilter();
  }

  onBlur() {
    this.onTouchedCb();

    // make sure that the panel is closed at some point...
    // this may not be immediately, and it may not even be on
    // the next event loop (timeout = 0ms), and it maybe NOT EVEN
    // be within the first 100ms. Otherwise selection via mouse
    // is no longer working :| 200ms is percieved near immediate
    // and this exists MAINLY for UI tests to make sure the suggestion
    // panel does not get in the way, so this is an acceptable
    // (although not nice) workaround.
    setTimeout(() => this.trigger.closePanel(), 200);
  }

  writeValue(v: any): void {
    if (v !== this.internalValue) {
      this.internalValue = v;
      this.updateFilter();
    }
  }

  registerOnChange(fn: any): void {
    this.onChangedCb = fn;
  }

  registerOnTouched(fn: any): void {
    this.onTouchedCb = fn;
  }

  isErrorState(control: UntypedFormControl | null): boolean {
    if (this.errorFallback) {
      return true;
    }

    if (!this.isInvalid()) {
      return false;
    }

    return this.errorDisplay === 'immediate' || !!(control && (control.dirty || control.touched));
  }

  public isInvalid() {
    return this.ngControl?.invalid;
  }

  public getErrorMessage() {
    if (!this.ngControl) {
      return null;
    }

    const msg = bdValidationMessage(this.label, this.ngControl.errors);

    if (msg) {
      return msg;
    }

    if (this.errorFallback) {
      return this.errorFallback;
    }
  }

  private updateFilter() {
    if (this.suggested === null || this.suggested === undefined) {
      // no suggestions at all.
      this.filteredSuggested$.next([]);
      return;
    }

    if (this.internalValue === null || this.internalValue === undefined) {
      // unfiltered.
      this.filteredSuggested$.next(this.suggested);
      return;
    }

    const inputAsString = this.internalValue.toString();

    if (!inputAsString.length) {
      // unfiltered
      this.filteredSuggested$.next(this.suggested);
      return;
    }

    this.filteredSuggested$.next(this.suggested.filter((e) => e.toLowerCase().includes(inputAsString.toLowerCase())));
  }
}
