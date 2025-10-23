/* eslint-disable @typescript-eslint/consistent-type-assertions */
/* eslint-disable @typescript-eslint/no-explicit-any */
import { AfterViewInit, Component, ElementRef, EventEmitter, OnDestroy, Output, ViewChild } from '@angular/core';
import { Subscription } from 'rxjs';
import { editor } from 'monaco-editor';
import IEditor = editor.IEditor;
import IEditorOptions = editor.IEditorOptions;

let loadedMonaco = false;
let loadPromise: Promise<void>;

@Component({
  template: '',
})

/**
 * Defines logic for initialising the monaco editor code.
 *
 * @param <T> Type of editor
 * @param <X> Type of options that should be used with that editor
 */
export abstract class BdMonacoBaseEditorComponent<T extends IEditor, X extends IEditorOptions>
  implements AfterViewInit, OnDestroy
{
  @ViewChild('editorContainer', { static: true }) _editorContainer: ElementRef;
  @Output() init = new EventEmitter<T>();
  protected _editor: T;
  protected _options: X;
  protected _windowResizeSubscription: Subscription;

  ngAfterViewInit(): void {
    if (loadedMonaco) {
      // Wait until monaco editor is available
      loadPromise.then(() => {
        this.initMonaco(this._options);
      });
    } else {
      loadedMonaco = true;
      loadPromise = new Promise<void>((resolve: () => void) => {
        const baseUrl = './assets/monaco-editor/min/vs';
        if (typeof (<any>globalThis).monaco === 'object') {
          resolve();
          return;
        }
        const onGotAmdLoader = () => {
          // Load monaco
          (<any>globalThis).require.config({ paths: { vs: `${baseUrl}` } });
          (<any>globalThis).require([`vs/editor/editor.main`], () => {
            this.initMonaco(this._options);
            resolve();
          });
        };

        // Load AMD loader if necessary
        if (!(<any>globalThis).require) {
          const loaderScript: HTMLScriptElement = document.createElement('script');
          loaderScript.type = 'text/javascript';
          loaderScript.src = `${baseUrl}/loader.js`;
          loaderScript.addEventListener('load', onGotAmdLoader);
          document.body.appendChild(loaderScript);
        } else {
          onGotAmdLoader();
        }
      });
    }
  }

  protected abstract initMonaco(options: X): void;

  ngOnDestroy() {
    if (this._windowResizeSubscription) {
      this._windowResizeSubscription.unsubscribe();
    }
    if (this._editor) {
      this._editor.dispose();
      this._editor = null;
    }
  }
}
