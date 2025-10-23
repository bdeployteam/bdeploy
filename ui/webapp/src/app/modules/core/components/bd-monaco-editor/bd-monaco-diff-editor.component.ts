import { Component, Input } from '@angular/core';
import { fromEvent } from 'rxjs';
import { BdMonacoBaseEditorComponent } from './bd-monaco-base-editor.component';
import { editor } from 'monaco-editor';
import { GlobalMonacoModule } from '../../services/monaco-completions.service';
import IStandaloneDiffEditor = editor.IStandaloneDiffEditor;
import IStandaloneDiffEditorConstructionOptions = editor.IStandaloneDiffEditorConstructionOptions;

declare let monaco: GlobalMonacoModule;

@Component({
  selector: 'app-bd-monaco-diff-editor',
  templateUrl: './bd-monaco-diff-editor.component.html',
  styleUrl: './bd-monaco-diff-editor.component.css',
})
export class BdMonacoDiffEditorComponent extends BdMonacoBaseEditorComponent<
  IStandaloneDiffEditor,
  IStandaloneDiffEditorConstructionOptions
> {
  @Input()
  set options(options: IStandaloneDiffEditorConstructionOptions) {
    this._options = { ...options };
    if (this._editor) {
      this._editor.dispose();
      this.initMonaco(options);
    }
  }

  get options(): IStandaloneDiffEditorConstructionOptions {
    return this._options;
  }

  protected initMonaco(options: IStandaloneDiffEditorConstructionOptions): void {
    this._editorContainer.nativeElement.innerHTML = '';
    const theme = options.theme;
    this._editor = monaco.editor.createDiffEditor(this._editorContainer.nativeElement, options);
    options.theme = theme;

    // refresh layout on resize event
    if (this._windowResizeSubscription) {
      this._windowResizeSubscription.unsubscribe();
    }
    this._windowResizeSubscription = fromEvent(globalThis, 'resize').subscribe(() => this._editor.layout());
    this.init.emit(this._editor);
  }
}
