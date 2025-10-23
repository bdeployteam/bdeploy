import {
  ChangeDetectorRef,
  Component,
  EventEmitter,
  inject,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
  SimpleChanges
} from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import {
  MonacoCompletionsService,
  GlobalMonacoModule, WindowWithMonacoLoaded
} from '../../services/monaco-completions.service';
import { ThemeService } from '../../services/theme.service';
import { ContentCompletion } from '../bd-content-assist-menu/bd-content-assist-menu.component';
import { FormsModule } from '@angular/forms';
import { AsyncPipe } from '@angular/common';
import { editor, languages, Position } from 'monaco-editor';
import ITextModel = editor.ITextModel;
import IEditor = editor.IEditor;
import CompletionItemProvider = languages.CompletionItemProvider;
import ProviderResult = languages.ProviderResult;
import CompletionList = languages.CompletionList;
import { BdMonacoEditorComponent } from '../bd-monaco-editor/bd-monaco-editor.component';
import IStandaloneCodeEditor = editor.IStandaloneCodeEditor;
import IStandaloneEditorConstructionOptions = editor.IStandaloneEditorConstructionOptions;

@Component({
    selector: 'app-bd-editor',
    templateUrl: './bd-editor.component.html',
    imports: [FormsModule, AsyncPipe, BdMonacoEditorComponent]
})
export class BdEditorComponent implements OnInit, OnDestroy, OnChanges {
  private readonly themeService = inject(ThemeService);
  private readonly editorCompletions = inject(MonacoCompletionsService);
  private readonly cd = inject(ChangeDetectorRef);

  private globalMonaco: GlobalMonacoModule;
  private monaco: IEditor;
  private subscription: Subscription;
  private editorPath = '';

  @Input() set content(v: string) {
    this.editorContent = v;
  }

  @Input() set path(v: string) {
    this.editorPath = v;
    this.onPathChange();
  }

  @Input() readonly: boolean;
  @Input() completions: ContentCompletion[] = [];
  @Input() recursivePrefixes: string[] = [];

  @Input() markerRegex: string;
  // might return null, which means nothing will be marked for the given FindMatch
  @Input() createMarker: (m: editor.FindMatch) => editor.IMarkerData;

  @Output() contentChange = new EventEmitter<string>();

  protected editorContent = '';
  protected editorOptions: IStandaloneEditorConstructionOptions;
  protected inited$ = new BehaviorSubject<boolean>(false);

  ngOnInit(): void {
    this.subscription = this.themeService.getThemeSubject().subscribe(() => {
      this.globalMonaco?.editor.setTheme(this.themeService.isDarkTheme() ? 'vs-dark' : 'vs');
    });

    this.editorOptions = {
      theme: this.themeService.isDarkTheme() ? 'vs-dark' : 'vs',
      language: 'plaintext',
      readOnly: this.readonly,
      minimap: { enabled: false },
      autoClosingBrackets: 'never',
      automaticLayout: true
    };
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['completions']) {
      this.editorCompletions.setCompletions(this.completions, this.recursivePrefixes);
    }
    if (changes['markerRegex'] || changes['createMarker']) {
      this.setModelMarkers();
    }
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected onMonacoInit(monaco: IStandaloneCodeEditor) {
    this.monaco = monaco;
    this.globalMonaco = (globalThis as unknown as WindowWithMonacoLoaded).monaco;
    // only do this once!
    if (!this.globalMonaco.__providerRegistered) {
      // register completion provider.
      const provider = this.createCompletionProvider(this.editorCompletions);
      this.globalMonaco.languages.getLanguages().forEach((l) => {
        this.globalMonaco.languages.registerCompletionItemProvider(l.id, provider);
        this.globalMonaco.__providerRegistered = true;
      });
    }

    // wait for init to complete, otherwise we leak models.
    setTimeout(() => this.onPathChange(), 0);

    // async init of monaco editor when the model changes - this is only for testing purposes.
    setTimeout(() => {
      this.inited$.next(true);
      this.cd.detectChanges();
    }, 1000);
  }

  protected onModelChange(event: string) {
    this.setModelMarkers();
    this.contentChange.emit(event);
  }

  private setModelMarkers() {
    if (!this.markerRegex || !this.createMarker || !this.globalMonaco) {
      return;
    }
    const model = this.globalMonaco.editor.getModel(this.globalMonaco.Uri.parse(this.editorPath));
    if (!model) {
      return;
    }
    const matches = model.findMatches(this.markerRegex, true, true, false, null, true);
    const markers = matches.map((m) => this.createMarker(m)).filter((m) => !!m);
    this.globalMonaco.editor.setModelMarkers(model, 'markers', markers);
  }

  private onPathChange() {
    if (!this.globalMonaco || !this.editorPath) {
      return;
    }

    this.globalMonaco.editor.getModels().forEach((m) => m.dispose());

    const model: ITextModel = this.globalMonaco.editor.createModel(
      this.editorContent,
      undefined,
      this.globalMonaco.Uri.parse(this.editorPath)
    );
    this.monaco.setModel(model);
    this.setModelMarkers();
  }

  private createCompletionProvider(editorCompletions: MonacoCompletionsService): CompletionItemProvider {
    // ATTENTION: the provider may NOT use ANYTHING from this component, as it will live globally, longer than this component.
    // Thus we're using a global service which will hold the currently valid completions. This also implies that ther cannot be
    // more than one set of completions at a time - thus IF there would be more than one editor, they'd share those.
    return {
      provideCompletionItems: (model: ITextModel, position: Position): ProviderResult<CompletionList> => {
        const searchString = model.getValueInRange({
          startLineNumber: position.lineNumber,
          startColumn: 1,
          endLineNumber: position.lineNumber,
          endColumn: position.column
        });

        // check if current word starts with '{{' and find the position.
        let wordBegin = searchString.lastIndexOf('{{');
        if (wordBegin < 0 || wordBegin > searchString.length) {
          wordBegin = 0;
        }

        const word = searchString.substring(wordBegin);

        if (!/^.*\{\{[^ \t]*/.exec(word)) {
          return { suggestions: [] };
        }

        const range = {
          startLineNumber: position.lineNumber,
          endLineNumber: position.lineNumber,
          startColumn: wordBegin + 1,
          endColumn: position.column
        };

        return {
          suggestions: editorCompletions.getCompletions(this.globalMonaco, word, range)
        };
      }
    };
  }
}
