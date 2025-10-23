import { ChangeDetectorRef, Component, EventEmitter, Input, OnDestroy, OnInit, Output, inject } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { ThemeService } from '../../services/theme.service';
import { AsyncPipe } from '@angular/common';
import { editor } from 'monaco-editor';
import IStandaloneDiffEditor = editor.IStandaloneDiffEditor;
import ITextModel = editor.ITextModel;
import { GlobalMonacoModule, WindowWithMonacoLoaded } from '../../services/monaco-completions.service';
import { BdMonacoDiffEditorComponent } from '../bd-monaco-editor/bd-monaco-diff-editor.component';
import IStandaloneDiffEditorConstructionOptions = editor.IStandaloneDiffEditorConstructionOptions;

@Component({
  selector: 'app-bd-editor-diff',
  templateUrl: './bd-editor-diff.component.html',
  imports: [AsyncPipe, BdMonacoDiffEditorComponent],
})
export class BdEditorDiffComponent implements OnInit, OnDestroy {
  private readonly themeService = inject(ThemeService);
  private readonly cd = inject(ChangeDetectorRef);

  private globalMonaco: GlobalMonacoModule;
  private monaco: IStandaloneDiffEditor;
  private subscription: Subscription;

  @Input() originalContent: string;
  @Input() modifiedContent: string;
  @Input() path = '';
  @Output() modifiedContentChange = new EventEmitter<string>();

  protected editorOptions: IStandaloneDiffEditorConstructionOptions = {
    theme: this.themeService.isDarkTheme() ? 'vs-dark' : 'vs',
    automaticLayout: true,
    glyphMargin: true,
  };

  protected inited$ = new BehaviorSubject<boolean>(false);

  ngOnInit(): void {
    this.subscription = this.themeService.getThemeSubject().subscribe(() => {
      this.globalMonaco?.editor.setTheme(this.themeService.isDarkTheme() ? 'vs-dark' : 'vs');
    });
  }

  public update() {
    this.initMonaco();
  }

  onMonacoInit(monaco: IStandaloneDiffEditor) {
    this.monaco = monaco;
    this.globalMonaco = (globalThis as unknown as WindowWithMonacoLoaded).monaco;

    // wait for init to complete, otherwise we leak models.
    setTimeout(() => this.initMonaco(), 0);

    // async init of monaco editor when the model changes - this is only for testing purposes.
    setTimeout(() => {
      this.inited$.next(true);
      this.cd.detectChanges();
    }, 1000);
  }

  initMonaco() {
    this.globalMonaco.editor.getModels().forEach((m: ITextModel) => m.dispose());
    const model = {
      original: this.globalMonaco.editor.createModel(
        this.originalContent,
        undefined,
        this.globalMonaco.Uri.parse(`a/${this.path}`)
      ),
      modified: this.globalMonaco.editor.createModel(
        this.modifiedContent,
        undefined,
        this.globalMonaco.Uri.parse(`b/${this.path}`)
      ),
    };
    this.monaco.setModel(model);
    this.monaco.getModifiedEditor().onDidChangeModelContent(() => {
      this.modifiedContentChange.emit(this.monaco.getModifiedEditor().getValue());
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }
}
