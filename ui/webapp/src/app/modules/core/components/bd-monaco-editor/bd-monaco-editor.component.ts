import { Component, forwardRef, inject, Input, NgZone } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { fromEvent } from 'rxjs';
import { BdMonacoBaseEditorComponent } from './bd-monaco-base-editor.component';
import { GlobalMonacoModule } from '../../services/monaco-completions.service';
import { editor } from 'monaco-editor';
import IStandaloneCodeEditor = editor.IStandaloneCodeEditor;
import IStandaloneEditorConstructionOptions = editor.IStandaloneEditorConstructionOptions;

declare let monaco: GlobalMonacoModule;

@Component({
  selector: 'app-bd-monaco-editor',
  templateUrl: './bd-monaco-editor.component.html',
  styleUrl: './bd-monaco-editor.component.css',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => BdMonacoEditorComponent),
      multi: true,
    },
  ],
})
export class BdMonacoEditorComponent
  extends BdMonacoBaseEditorComponent<IStandaloneCodeEditor, IStandaloneEditorConstructionOptions>
  implements ControlValueAccessor
{
  private readonly zone = inject(NgZone);

  private _value = '';

  propagateChange: (_: unknown) => void = () => {
    /* intentionally empty */
  };
  onTouched: () => void = () => {
    /* intentionally empty */
  };

  @Input()
  set options(options: IStandaloneEditorConstructionOptions) {
    this._options = { ...options };
    if (this._editor) {
      this._editor.dispose();
      this.initMonaco(options);
    }
  }

  get options(): IStandaloneEditorConstructionOptions {
    return this._options;
  }

  writeValue(value: string): void {
    this._value = value || '';
    // Fix for value change while dispose in process
    setTimeout(() => {
      if (this._editor) {
        this._editor.setValue(this._value);
      }
    });
  }

  registerOnChange(fn: (_: unknown) => void): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  protected initMonaco(options: IStandaloneEditorConstructionOptions): void {
    this._editor = monaco.editor.create(this._editorContainer.nativeElement, options);

    this._editor.setValue(this._value);

    this._editor.onDidChangeModelContent(() => {
      const value = this._editor.getValue();

      // value is not propagated to parent when executing outside zone
      this.zone.run(() => {
        this.propagateChange(value);
        this._value = value;
      });
    });

    this._editor.onDidBlurEditorWidget(() => {
      this.onTouched();
    });

    // refresh layout on resize event
    if (this._windowResizeSubscription) {
      this._windowResizeSubscription.unsubscribe();
    }
    this._windowResizeSubscription = fromEvent(globalThis, 'resize').subscribe(() => this._editor.layout());
    this.init.emit(this._editor);
  }
}
