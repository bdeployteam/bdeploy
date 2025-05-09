import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  inject,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  TemplateRef,
  ViewChild,
  ViewEncapsulation
} from '@angular/core';
import { ControlValueAccessor, NgControl, UntypedFormControl, FormsModule } from '@angular/forms';
import { MatAutocompleteTrigger, MatAutocomplete } from '@angular/material/autocomplete';
import { ErrorStateMatcher, MatOption } from '@angular/material/core';
import { BehaviorSubject } from 'rxjs';
import { bdValidationMessage } from '../../validators/messages';
import { ContentCompletion, BdContentAssistMenuComponent } from '../bd-content-assist-menu/bd-content-assist-menu.component';
import { MatFormField, MatLabel, MatPrefix, MatSuffix, MatError, MatHint } from '@angular/material/form-field';
import { ClickStopPropagationDirective } from '../../directives/click-stop-propagation.directive';
import { MatInput } from '@angular/material/input';
import { BdContentAssistDirective } from '../bd-content-assist/bd-content-assist.directive';
import { CdkAutofill } from '@angular/cdk/text-field';
import { NgTemplateOutlet, AsyncPipe } from '@angular/common';
import { MatIcon } from '@angular/material/icon';
import { MatCard } from '@angular/material/card';
import { BdPopupDirective } from '../bd-popup/bd-popup.directive';

@Component({
    selector: 'app-bd-form-input',
    templateUrl: './bd-form-input.component.html',
    styleUrls: ['./bd-form-input.component.css'],
    encapsulation: ViewEncapsulation.None,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [MatFormField, ClickStopPropagationDirective, MatLabel, BdContentAssistMenuComponent, MatInput, MatAutocompleteTrigger, FormsModule, BdContentAssistDirective, CdkAutofill, MatAutocomplete, MatOption, MatPrefix, NgTemplateOutlet, MatSuffix, MatIcon, MatError, MatCard, MatHint, BdPopupDirective, AsyncPipe]
})
export class BdFormInputComponent implements ControlValueAccessor, ErrorStateMatcher, OnChanges {
  protected readonly ngControl = inject(NgControl, { self: true, optional: true });
  protected readonly elementRef = inject(ElementRef);

  @Input() label: string;
  @Input() name: string;
  @Input() required: boolean | string;
  @Input() disabled: boolean | string;
  @Input() type: string;
  @Input() placeholder: string;
  @Input() suggested: string[];
  @Input() errorDisplay: 'touched' | 'immediate' = 'touched';
  @Input() passwordLock = false;
  @Input() prefix: TemplateRef<unknown>;
  @Input() assistValues: ContentCompletion[];
  @Input() assistPrefixes: ContentCompletion[];
  @Input() errorFallback: string;
  @Input() statusMessage: string;
  @Input() warningMessage: string;
  @Input() passwordShowable = false;

  @Output() focusIn = new EventEmitter<boolean>();

  @ViewChild(MatAutocompleteTrigger) private readonly trigger: MatAutocompleteTrigger;

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

  private internalValue: unknown = '';
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
    this.updateFilter();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['suggested']) {
      this.updateFilter();
    }
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

  writeValue(v: unknown): void {
    if (v !== this.internalValue) {
      this.internalValue = v;
      this.updateFilter();
    }
  }

  registerOnChange(fn: (_: unknown) => void): void {
    this.onChangedCb = fn;
  }

  registerOnTouched(fn: () => void): void {
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

  public hasErrorMessage(): boolean {
    return !!this.errorFallback || this.ngControl?.invalid;
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

    throw new Error('Missing error message and fallback for ' + this.label);
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
