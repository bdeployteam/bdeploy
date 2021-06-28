import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { Subscription } from 'rxjs';
import { ThemeService } from 'src/app/modules/core/services/theme.service';

@Component({
  selector: 'app-file-editor',
  templateUrl: './file-editor.component.html',
  styleUrls: ['./file-editor.component.css'],
})
export class FileEditorComponent implements OnInit, OnDestroy {
  private globalMonaco;
  private monaco;

  private themeSubscription: Subscription;

  @Input()
  public set content(v: string) {
    this.editorContent = v;
  }

  @Output()
  public contentChange = new EventEmitter<string>();

  @Input()
  public set path(v: string) {
    this.editorPath = v;
    this.onPathChange();
  }

  public editorContent = '';
  private editorPath = '';

  public editorOptions = {
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

  ngOnDestroy(): void {
    this.themeSubscription.unsubscribe();
  }

  onMonacoInit(monaco) {
    this.monaco = monaco;
    this.globalMonaco = window['monaco'];

    // wait for init to complete, otherwise we leak models.
    setTimeout(() => this.onPathChange(), 0);
  }

  onPathChange() {
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

  onModelChange(event: string) {
    this.contentChange.emit(event);
  }
}
