import { Component, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
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

  private cachedX = 0;
  private cachedY = 0;

  private relayoutInterval;

  @Input() originalContent: string;
  @Input() modifiedContent: string;
  @Input() path: string = '';
  @Output() modifiedContentChange = new EventEmitter<string>();

  /* template */ editorOptions = {
    theme: this.themeService.isDarkTheme() ? 'vs-dark' : 'vs',
    language: 'plaintext',
  };

  /* template */ inited = false;

  constructor(private themeService: ThemeService, private host: ElementRef) {}

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

    // this is required sind monaco does not play well inside flex (changing) layouts.
    this.relayoutInterval = setInterval(() => this.layoutCheck(), 100);
  }

  private layoutCheck() {
    const x = this.host.nativeElement.offsetWidth;
    const y = this.host.nativeElement.offsetHeight;

    if (x !== this.cachedX || y != this.cachedY) {
      this.cachedX = x;
      this.cachedY = y;
      this.monaco.layout();
    }
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

    this.inited = true;
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    clearInterval(this.relayoutInterval);
  }
}
