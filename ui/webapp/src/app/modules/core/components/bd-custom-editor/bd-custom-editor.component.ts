import { HttpErrorResponse } from '@angular/common/http';
import {
  AfterViewInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  QueryList,
  ViewChildren,
  inject,
} from '@angular/core';
import { Subscription } from 'rxjs';
import { CustomEditor, LinkedValueConfiguration, ManifestKey, PluginInfoDto } from 'src/app/models/gen.dtos';
import { EditorPlugin } from 'src/app/modules/core/plugins/plugin.editor';
import { PluginService } from 'src/app/modules/core/services/plugin.service';
import { getPreRenderable } from 'src/app/modules/core/utils/linked-values.utils';
import { BdPopupDirective } from '../bd-popup/bd-popup.directive';

@Component({
  selector: 'app-bd-custom-editor',
  templateUrl: './bd-custom-editor.component.html',
  styleUrls: ['./bd-custom-editor.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BdCustomEditorComponent implements OnChanges, OnDestroy, AfterViewInit {
  private plugins = inject(PluginService);
  private cd = inject(ChangeDetectorRef);

  @Input() customEditor: string;
  @Input() value: LinkedValueConfiguration;
  @Input() readonly: boolean;
  @Input() product: ManifestKey;
  @Input() group: string;

  @Output() valueConfirmed: EventEmitter<string> = new EventEmitter<string>();
  @Output() pluginLoaded: EventEmitter<CustomEditor> = new EventEmitter<CustomEditor>();

  @ViewChildren('editorPanel') editorPanels: QueryList<ElementRef<any>>;

  protected plugin: PluginInfoDto;
  protected error: HttpErrorResponse;
  protected valid = false;
  protected popup: BdPopupDirective;
  protected editor: EditorPlugin;

  private currentValue: string;
  private subscription: Subscription;

  ngOnChanges(): void {
    if (!this.group || !this.product || !this.customEditor) {
      return;
    }

    this.plugins.getEditorPlugin(this.group, this.product, this.customEditor).subscribe((r) => {
      this.plugin = r;
      this.pluginLoaded.emit(this.findEditor());
    });
  }

  ngAfterViewInit(): void {
    this.subscription = this.editorPanels.changes.subscribe((p) => {
      if (!this.editor) {
        return;
      }

      p.forEach((elem) => {
        setTimeout(() =>
          elem.nativeElement.appendChild(
            this.editor.bind(
              () => getPreRenderable(this.value),
              (v) => {
                this.currentValue = v;
                this.cd.markForCheck();
              },
              (s) => {
                this.valid = s;
                this.cd.markForCheck();
              }
            )
          )
        );
      });
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected prepareEditor() {
    if (!this.plugin) {
      return;
    }
    this.plugins.load(this.plugin, this.findEditor().modulePath).then((m) => {
      this.currentValue = getPreRenderable(this.value);
      this.editor = new m.default(this.plugins.getApi(this.plugin)) as EditorPlugin;

      this.cd.markForCheck();
    });
  }

  protected apply() {
    this.editor = null;
    this.valueConfirmed.emit(this.currentValue);
    this.popup.closeOverlay();
  }

  private findEditor(): CustomEditor {
    return this.plugin.editors.find((e) => e.typeName === this.customEditor);
  }
}
