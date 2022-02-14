import {
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnDestroy,
  OnInit,
  Output,
} from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
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
  private relayoutInterval;

  private cachedX = 0;
  private cachedY = 0;

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
  /* template */ inited$ = new BehaviorSubject<boolean>(false);

  constructor(private themeService: ThemeService, private host: ElementRef) {}

  ngOnInit(): void {
    this.subscription = this.themeService.getThemeSubject().subscribe(() => {
      if (this.globalMonaco) {
        this.globalMonaco.editor.setTheme(
          this.themeService.isDarkTheme() ? 'vs-dark' : 'vs'
        );
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
    clearInterval(this.relayoutInterval);
  }

  /* template */ onMonacoInit(monaco) {
    this.monaco = monaco;
    this.globalMonaco = window['monaco'];

    // wait for init to complete, otherwise we leak models.
    setTimeout(() => this.onPathChange(), 0);

    // this is required sind monaco does not play well inside flex (changing) layouts.
    this.relayoutInterval = setInterval(() => this.layoutCheck(), 100);

    // async init of monaco editor when the model changes - this is only for testing purposes.
    setTimeout(() => {
      this.inited$.next(true);
    }, 1000);
  }

  /* template */ onModelChange(event: string) {
    this.contentChange.emit(event);
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
}
