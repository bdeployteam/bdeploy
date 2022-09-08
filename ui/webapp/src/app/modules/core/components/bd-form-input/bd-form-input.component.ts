import {
  Component,
  ElementRef,
  Input,
  OnInit,
  Optional,
  Self,
  TemplateRef,
  ViewEncapsulation,
} from '@angular/core';
import {
  ControlValueAccessor,
  NgControl,
  UntypedFormControl,
} from '@angular/forms';
import { ErrorStateMatcher } from '@angular/material/core';
import { BehaviorSubject } from 'rxjs';
import { bdValidationMessage } from '../../validators/messages';
import { ContentCompletion } from '../bd-content-assist-menu/bd-content-assist-menu.component';

@Component({
  selector: 'app-bd-form-input',
  templateUrl: './bd-form-input.component.html',
  styleUrls: ['./bd-form-input.component.css'],
  encapsulation: ViewEncapsulation.None,
})
export class BdFormInputComponent
  implements OnInit, ControlValueAccessor, ErrorStateMatcher
{
  @Input() label: string;
  @Input() name: string;
  @Input() required: any;
  @Input() disabled: any;
  @Input() type: string;
  @Input() suggested: string[];
  @Input() errorDisplay: 'touched' | 'immediate' = 'touched';
  @Input() passwordLock = false;
  @Input() prefix: TemplateRef<any>;
  @Input() assistValues: ContentCompletion[];
  @Input() assistPrefixes: ContentCompletion[];
  @Input() errorFallback: string;
  @Input() statusMessage: string;

  /* template */ filteredSuggested$ = new BehaviorSubject<string[]>([]);

  /* template */ get value() {
    return this.internalValue;
  }
  /* template */ set value(v) {
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

  constructor(
    @Optional() @Self() public ngControl: NgControl,
    public elementRef: ElementRef
  ) {
    if (ngControl) {
      ngControl.valueAccessor = this;
    }
  }

  ngOnInit(): void {
    this.updateFilter();
  }

  onBlur() {
    this.onTouchedCb();
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

    return (
      this.errorDisplay === 'immediate' ||
      !!(control && (control.dirty || control.touched))
    );
  }

  public isInvalid() {
    return this.ngControl && this.ngControl.invalid;
  }

  /* template */ getErrorMessage() {
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

    this.filteredSuggested$.next(
      this.suggested.filter((e) =>
        e.toLowerCase().includes(inputAsString.toLowerCase())
      )
    );
  }
}
