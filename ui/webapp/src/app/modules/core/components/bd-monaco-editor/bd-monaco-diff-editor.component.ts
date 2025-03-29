/* eslint-disable @angular-eslint/no-input-rename */
import { Component, Input } from '@angular/core';
import { fromEvent } from 'rxjs';

import { BdMonacoBaseEditorComponent } from './bd-monaco-base-editor.component';

declare let monaco: any;

@Component({
  selector: 'app-bd-monaco-diff-editor',
  templateUrl: './bd-monaco-diff-editor.component.html',
  styleUrl: './bd-monaco-diff-editor.component.css'
})
export class BdMonacoDiffEditorComponent extends BdMonacoBaseEditorComponent {

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

  protected initMonaco(options: any): void {
    this._editorContainer.nativeElement.innerHTML = '';
    const theme = options.theme;
    this._editor = monaco.editor.createDiffEditor(this._editorContainer.nativeElement, options);
    options.theme = theme;

    // refresh layout on resize event.
    if (this._windowResizeSubscription) {
      this._windowResizeSubscription.unsubscribe();
    }
    this._windowResizeSubscription = fromEvent(window, 'resize').subscribe(() => this._editor.layout());
    this.onInit.emit(this._editor);
  }

}
