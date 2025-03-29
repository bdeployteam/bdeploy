/* eslint-disable @typescript-eslint/no-empty-function */
import { Component, forwardRef, inject, Input, NgZone } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { fromEvent } from 'rxjs';

import { BdMonacoBaseEditorComponent } from './bd-monaco-base-editor.component';

declare let monaco: any;

@Component({
  selector: 'app-bd-monaco-editor',
  templateUrl: './bd-monaco-editor.component.html',
  styleUrl: './bd-monaco-editor.component.css',
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => BdMonacoEditorComponent),
    multi: true
  }
  ]
})
export class BdMonacoEditorComponent extends BdMonacoBaseEditorComponent implements ControlValueAccessor {
  private zone = inject(NgZone);

  private _value = '';

  propagateChange = (_: any) => {};
  onTouched = () => {};

  @Input()
  set options(options: any) {
    this._options = Object.assign({}, options);
    if (this._editor) {
      this._editor.dispose();
      this.initMonaco(options);
    }
  }

  get options(): any {
    return this._options;
  }

  writeValue(value: any): void {
    this._value = value || '';
    // Fix for value change while dispose in process.
    setTimeout(() => {
      if (this._editor) {
        this._editor.setValue(this._value);
      }
    });
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
    this.onTouched = fn;
  }

  protected initMonaco(options: any): void {

    this._editor = monaco.editor.create(this._editorContainer.nativeElement, options);

    this._editor.setValue(this._value);

    this._editor.onDidChangeModelContent((e: any) => {
      const value = this._editor.getValue();

      // value is not propagated to parent when executing outside zone.
      this.zone.run(() => {
        this.propagateChange(value);
        this._value = value;
      });
    });

    this._editor.onDidBlurEditorWidget(() => {
      this.onTouched();
    });

    // refresh layout on resize event.
    if (this._windowResizeSubscription) {
      this._windowResizeSubscription.unsubscribe();
    }
    this._windowResizeSubscription = fromEvent(window, 'resize').subscribe(() => this._editor.layout());
    this.onInit.emit(this._editor);
  }

}
