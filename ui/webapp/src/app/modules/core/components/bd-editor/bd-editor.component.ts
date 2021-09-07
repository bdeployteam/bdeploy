import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { Subscription } from 'rxjs';
import { ThemeService } from '../../services/theme.service';

@Component({
  selector: 'app-bd-editor',
  templateUrl: './bd-editor.component.html',
  styleUrls: ['./bd-editor.component.css'],
})
export class BdEditorComponent implements OnInit, OnDestroy {
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

  @Output() contentChange = new EventEmitter<string>();

  /* template */ editorContent = '';
  /* template */ editorOptions;

  constructor(private themeService: ThemeService) {}

  ngOnInit(): void {
    this.subscription = this.themeService.getThemeSubject().subscribe((theme) => {
      if (this.globalMonaco) {
        this.globalMonaco.editor.setTheme(this.themeService.isDarkTheme() ? 'vs-dark' : 'vs');
      }
    });

    this.editorOptions = {
      theme: this.themeService.isDarkTheme() ? 'vs-dark' : 'vs',
      language: 'plaintext',
      readOnly: this.readonly,
      minimap: { enabled: false },
    };
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ onMonacoInit(monaco) {
    this.monaco = monaco;
    this.globalMonaco = window['monaco'];

    // wait for init to complete, otherwise we leak models.
    setTimeout(() => this.onPathChange(), 0);
  }

  /* template */ onModelChange(event: string) {
    this.contentChange.emit(event);
  }

  private onPathChange() {
    if (!this.globalMonaco || !this.editorPath) {
      return;
    }

    this.globalMonaco.editor.getModels().forEach((m) => m.dispose());

    const model = this.globalMonaco.editor.createModel(this.editorContent, undefined, this.globalMonaco.Uri.parse(this.editorPath));
    this.monaco.setModel(model);
  }
}
