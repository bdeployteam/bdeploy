import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { Subscription } from 'rxjs';
import { ThemeService } from '../../services/theme.service';

@Component({
  selector: 'app-bd-editor-diff',
  templateUrl: './bd-editor-diff.component.html',
  styleUrls: ['./bd-editor-diff.component.css'],
})
export class BdEditorDiffComponent implements OnInit, OnDestroy {
  private globalMonaco;
  private monaco;
  private subscription: Subscription;

  @Input() originalContent: string;
  @Input() modifiedContent: string;
  @Input() path: string = '';
  @Output() modifiedContentChange = new EventEmitter<string>();

  /* template */ editorOptions = {
    theme: this.themeService.isDarkTheme() ? 'vs-dark' : 'vs',
    language: 'plaintext',
  };

  constructor(private themeService: ThemeService) {}

  ngOnInit(): void {
    this.subscription = this.themeService.getThemeSubject().subscribe((theme) => {
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
      modified: this.globalMonaco.editor.createModel(this.modifiedContent, undefined, this.globalMonaco.Uri.parse(this.path)),
    };
    this.monaco.setModel(model);
    this.monaco.getModifiedEditor().onDidChangeModelContent((e) => {
      this.modifiedContentChange.emit(this.monaco.getModifiedEditor().getValue());
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
