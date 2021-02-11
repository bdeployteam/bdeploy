import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { Subscription } from 'rxjs';
import { ThemeService } from 'src/app/modules/core/services/theme.service';

@Component({
  selector: 'app-diff-editor',
  templateUrl: './diff-editor.component.html',
  styleUrls: ['./diff-editor.component.css'],
})
export class DiffEditorComponent implements OnInit, OnDestroy {
  private globalMonaco;
  private monaco;
  private themeSubscription: Subscription;

  @Input()
  public originalContent: string;
  @Input()
  public modifiedContent: string;
  @Input()
  public path: string = '';
  @Output()
  public modifiedContentChange = new EventEmitter<string>();

  editorOptions = {
    theme: this.themeService.isDarkTheme() ? 'vs-dark' : 'vs',
    language: 'plaintext',
  };

  constructor(private themeService: ThemeService) {}

  ngOnInit(): void {
    this.themeSubscription = this.themeService.getThemeSubject().subscribe((theme) => {
      if (this.globalMonaco) {
        this.globalMonaco.editor.setTheme(this.themeService.isDarkTheme() ? 'vs-dark' : 'vs');
      }
    });
  }

  onMonacoInit(monaco) {
    this.monaco = monaco;
    this.globalMonaco = window['monaco'];

    // wait for init to complete, otherwise we leak models.
    setTimeout(() => this.initMonaco(), 0);
  }

  initMonaco() {
    this.globalMonaco.editor.getModels().forEach((m) => m.dispose());
    const model = {
      original: this.globalMonaco.editor.createModel(this.originalContent, undefined),
      modified: this.globalMonaco.editor.createModel(
        this.modifiedContent,
        undefined,
        this.globalMonaco.Uri.parse(this.path)
      ),
    };
    this.monaco.setModel(model);
    this.monaco.getModifiedEditor().onDidChangeModelContent((e) => {
      this.modifiedContentChange.emit(this.monaco.getModifiedEditor().getValue());
    });
  }

  ngOnDestroy(): void {
    this.themeSubscription.unsubscribe();
  }
}
