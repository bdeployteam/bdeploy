import {
  ChangeDetectorRef,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
  SimpleChanges,
  inject,
} from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { MonacoCompletionsService } from '../../services/monaco-completions.service';
import { ThemeService } from '../../services/theme.service';
import { ContentCompletion } from '../bd-content-assist-menu/bd-content-assist-menu.component';

@Component({
  selector: 'app-bd-editor',
  templateUrl: './bd-editor.component.html',
})
export class BdEditorComponent implements OnInit, OnDestroy, OnChanges {
  private themeService = inject(ThemeService);
  private editorCompletions = inject(MonacoCompletionsService);
  private cd = inject(ChangeDetectorRef);

  private globalMonaco;
  private monaco;
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

  @Output() contentChange = new EventEmitter<string>();

  protected editorContent = '';
  protected editorOptions;
  protected inited$ = new BehaviorSubject<boolean>(false);

  ngOnInit(): void {
    this.subscription = this.themeService.getThemeSubject().subscribe(() => {
      if (this.globalMonaco) {
        this.globalMonaco.editor.setTheme(this.themeService.isDarkTheme() ? 'vs-dark' : 'vs');
      }
    });

    this.editorOptions = {
      theme: this.themeService.isDarkTheme() ? 'vs-dark' : 'vs',
      language: 'plaintext',
      readOnly: this.readonly,
      minimap: { enabled: false },
      autoClosingBrackets: false,
      automaticLayout: true,
    };
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.completions) {
      this.editorCompletions.setCompletions(this.completions);
    }
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected onMonacoInit(monaco) {
    this.monaco = monaco;
    this.globalMonaco = window['monaco'];

    // only do this once!
    if (!this.globalMonaco['__providerRegistered']) {
      // register completion provider.
      const provider = this.createCompletionProvider(this.editorCompletions);
      this.globalMonaco.languages.getLanguages().forEach((l) => {
        this.globalMonaco.languages.registerCompletionItemProvider(l.id, provider);
        this.globalMonaco['__providerRegistered'] = true;
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
    this.contentChange.emit(event);
  }

  private onPathChange() {
    if (!this.globalMonaco || !this.editorPath) {
      return;
    }

    this.globalMonaco.editor.getModels().forEach((m) => m.dispose());

    const model = this.globalMonaco.editor.createModel(
      this.editorContent,
      undefined,
      this.globalMonaco.Uri.parse(this.editorPath)
    );
    this.monaco.setModel(model);
  }

  private createCompletionProvider(editorCompletions: MonacoCompletionsService): any {
    // ATTENTION: the provider may NOT use ANYTHING from this component, as it will live globally, longer than this component.
    // Thus we're using a global service which will hold the currently valid completions. This also implies that ther cannot be
    // more than one set of completions at a time - thus IF there would be more than one editor, they'd share those.
    return {
      triggerCharacter: ['{'],
      provideCompletionItems: (model, position) => {
        const searchString = model.getValueInRange({
          startLineNumber: position.lineNumber,
          startColumn: 1,
          endLineNumber: position.lineNumber,
          endColumn: position.column,
        });

        // check if current word starts with '{{' and find the position.
        let wordBegin = searchString.lastIndexOf('{{');
        if (wordBegin < 0 || wordBegin > searchString.length) {
          wordBegin = 0;
        }

        const word = searchString.substring(wordBegin) as string;

        if (!word.match(/^.*\{\{[^ \t]*/)) {
          return { suggestions: [] };
        }

        const range = {
          startLineNumber: position.lineNumber,
          endLineNumber: position.lineNumber,
          startColumn: wordBegin + 1,
          endColumn: position.column,
        };

        const kindByIcon = (icon: string) => {
          // see completion.utils.ts!
          switch (icon) {
            case 'data_object': // instance & system variable.
              return this.globalMonaco.languages.CompletionItemKind.Variable;
            case 'build': // process parameters
              return this.globalMonaco.languages.CompletionItemKind.Keword;
            case 'folder': // deployment folders
              return this.globalMonaco.languages.CompletionItemKind.Folder;
            case 'dns': // host and environment
              return this.globalMonaco.languages.CompletionItemKind.User;
            case 'settings_system_daydream': // instance properties
              return this.globalMonaco.languages.CompletionItemKind.Class;
            case 'folder_special': // manifest reference
              return this.globalMonaco.languages.CompletionItemKind.Reference;
            case 'schedule': // delayed
              return this.globalMonaco.languages.CompletionItemKind.Operator;
            case 'devices_other': // os expansion
              return this.globalMonaco.languages.CompletionItemKind.Interface;
          }
          return this.globalMonaco.languages.CompletionItemKind.Constant; // should not happen
        };

        return {
          suggestions: editorCompletions.getCompletions()?.map((c) => ({
            label: c.value,
            insertText: c.value,
            detail: c.description,
            kind: kindByIcon(c.icon),
            range: range,
          })),
        };
      },
    };
  }
}
