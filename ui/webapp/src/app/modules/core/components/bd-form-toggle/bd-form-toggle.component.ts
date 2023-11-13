import {
  ChangeDetectionStrategy,
  Component,
  Input,
  TemplateRef,
  ViewChild,
  ViewEncapsulation,
  inject,
} from '@angular/core';
import { ControlValueAccessor, NgControl } from '@angular/forms';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatSlideToggle } from '@angular/material/slide-toggle';

@Component({
  selector: 'app-bd-form-toggle',
  templateUrl: './bd-form-toggle.component.html',
  styleUrls: ['./bd-form-toggle.component.css'],
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BdFormToggleComponent implements ControlValueAccessor {
  protected ngControl = inject(NgControl, { self: true, optional: true });

  @Input() label: string;
  @Input() name: string;
  @Input() disabled: boolean;
  @Input() appearance: 'slide' | 'checkbox' = 'checkbox';
  @Input() prefix: TemplateRef<any>;

  @ViewChild(MatCheckbox, { static: false })
  private checkbox: MatCheckbox;
  @ViewChild(MatSlideToggle, { static: false }) private slide: MatSlideToggle;

  protected get value() {
    return this.internalValue;
  }
  protected set value(v) {
    if (v !== this.internalValue) {
      this.internalValue = v;
      this.onTouchedCb();
      this.onChangedCb(v);
    }
  }

  private internalValue: any = '';
  // eslint-disable-next-line @typescript-eslint/no-empty-function
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

  protected onClick() {
    if (this.disabled) {
      return;
    }
    if (this.checkbox) {
      this.checkbox.toggle();
      this.value = this.checkbox.checked;
    }
    if (this.slide) {
      this.slide.toggle();
      this.value = this.slide.checked;
    }
  }
}
