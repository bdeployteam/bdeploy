import { Component, Input, OnInit, Optional, Self } from '@angular/core';
import { ControlValueAccessor, NgControl } from '@angular/forms';
import { MatCheckbox } from '@angular/material/checkbox';

@Component({
  selector: 'app-bd-form-toggle',
  templateUrl: './bd-form-toggle.component.html',
  styleUrls: ['./bd-form-toggle.component.css'],
})
export class BdFormToggleComponent implements OnInit, ControlValueAccessor {
  @Input() label: string;
  @Input() name: string;
  @Input() disabled: boolean;

  /* template */ get value() {
    return this.internalValue;
  }
  /* template */ set value(v) {
    if (v !== this.internalValue) {
      this.internalValue = v;
      this.onTouchedCb();
      this.onChangedCb(v);
    }
  }

  private internalValue: any = '';
  private onTouchedCb: () => void = () => {};
  private onChangedCb: (_: any) => void = () => {};

  constructor(@Optional() @Self() private ngControl: NgControl) {
    if (!!ngControl) {
      ngControl.valueAccessor = this;
    }
  }

  ngOnInit(): void {}

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

  /* template */
  onClick(check: MatCheckbox) {
    if (this.disabled) {
      return;
    }
    check.toggle();
    this.value = check.checked;
  }
}
