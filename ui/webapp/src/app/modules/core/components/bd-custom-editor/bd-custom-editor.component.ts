import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { HttpErrorResponse } from '@angular/common/http';
import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  QueryList,
  ViewChildren,
  ViewContainerRef,
} from '@angular/core';
import { Subscription } from 'rxjs';
import {
  CustomEditor,
  LinkedValueConfiguration,
  ManifestKey,
  PluginInfoDto,
} from 'src/app/models/gen.dtos';
import { EditorPlugin } from 'src/app/modules/core/plugins/plugin.editor';
import { PluginService } from 'src/app/modules/core/services/plugin.service';
import { getPreRenderable } from 'src/app/modules/core/utils/linked-values.utils';
import { BdPopupDirective } from '../bd-popup/bd-popup.directive';

@Component({
  selector: 'app-bd-custom-editor',
  templateUrl: './bd-custom-editor.component.html',
  styleUrls: ['./bd-custom-editor.component.css'],
})
export class BdCustomEditorComponent
  implements OnChanges, OnDestroy, AfterViewInit
{
  @Input() customEditor: string;
  @Input() value: LinkedValueConfiguration;
  @Input() readonly: boolean;
  @Input() product: ManifestKey;
  @Input() group: string;

  @Output() valueConfirmed: EventEmitter<string> = new EventEmitter<string>();
  @Output() pluginLoaded: EventEmitter<CustomEditor> =
    new EventEmitter<CustomEditor>();

  @ViewChildren('editorPanel') editorPanels: QueryList<ElementRef<any>>;

  /* template */ plugin: PluginInfoDto;
  /* template */ error: HttpErrorResponse;
  /* template */ valid = false;
  /* template */ popup: BdPopupDirective;

  private currentValue: string;
  private subscription: Subscription;

  /* template */ editor: EditorPlugin;

  private overlayRef: OverlayRef;

  constructor(
    private plugins: PluginService,
    private overlay: Overlay,
    private viewContainerRef: ViewContainerRef
  ) {}

  ngOnChanges(): void {
    if (!this.group || !this.product || !this.customEditor) {
      return;
    }

    this.plugins
      .getEditorPlugin(this.group, this.product, this.customEditor)
      .subscribe((r) => {
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
              (v) => (this.currentValue = v),
              (s) => (this.valid = s)
            )
          )
        );
      });
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  /* template */ prepareEditor() {
    if (!this.plugin) {
      return;
    }
    this.plugins.load(this.plugin, this.findEditor().modulePath).then((m) => {
      this.currentValue = getPreRenderable(this.value);
      this.editor = new m.default(
        this.plugins.getApi(this.plugin)
      ) as EditorPlugin;
    });
  }

  /* template */ apply() {
    this.editor = null;
    this.valueConfirmed.emit(this.currentValue);
    this.popup.closeOverlay();
  }

  private findEditor(): CustomEditor {
    return this.plugin.editors.find((e) => e.typeName === this.customEditor);
  }
}
