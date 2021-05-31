import { HttpErrorResponse } from '@angular/common/http';
import { AfterViewInit, Component, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output, QueryList, TemplateRef, ViewChildren } from '@angular/core';
import { combineLatest, Subscription } from 'rxjs';
import { filter, finalize, mergeMap } from 'rxjs/operators';
import { CustomEditor, ParameterConfiguration, ParameterDescriptor, PluginInfoDto } from 'src/app/models/gen.dtos';
import { ACTION_CANCEL, ACTION_OK } from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { EditorPlugin } from 'src/app/modules/core/plugins/plugin.editor';
import { PluginService } from 'src/app/modules/core/services/plugin.service';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { ProcessEditService } from '../../../../services/process-edit.service';

@Component({
  selector: 'app-custom-editor',
  templateUrl: './custom-editor.component.html',
  styleUrls: ['./custom-editor.component.css'],
})
export class CustomEditorComponent implements OnInit, OnDestroy, AfterViewInit {
  @Input() descriptor: ParameterDescriptor;
  @Input() value: ParameterConfiguration;
  @Input() dialog: BdDialogComponent;
  @Input() readonly: boolean;

  @Output() valueConfirmed: EventEmitter<any> = new EventEmitter<any>();
  @Output() pluginLoaded: EventEmitter<CustomEditor> = new EventEmitter<CustomEditor>();

  @ViewChildren('editorPanel') editorPanels: QueryList<ElementRef<any>>;

  /* template */ plugin: PluginInfoDto;
  /* template */ error: HttpErrorResponse;

  private currentValue: any;
  private valid = false;
  private subscription: Subscription;

  private editor: EditorPlugin;

  constructor(private plugins: PluginService, private groups: GroupsService, private edit: ProcessEditService) {}

  ngOnInit(): void {
    this.subscription = combineLatest([this.groups.current$, this.edit.product$])
      .pipe(
        filter(([g, p]) => !!g && !!p),
        mergeMap(([group, product]) => {
          return this.plugins.getEditorPlugin(group.name, product.key, this.descriptor.customEditor);
        })
      )
      .subscribe((r) => {
        this.plugin = r;
        this.pluginLoaded.emit(this.findEditor());
      });
  }

  ngAfterViewInit(): void {
    this.subscription.add(
      this.editorPanels.changes.subscribe((p) => {
        if (!this.editor) {
          return;
        }

        p.forEach((elem) => {
          setTimeout(() =>
            elem.nativeElement.appendChild(
              this.editor.bind(
                () => this.value.value,
                (v) => (this.currentValue = v),
                (s) => (this.valid = s)
              )
            )
          );
        });
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  showEditor(popup: TemplateRef<any>) {
    if (!this.plugin) {
      return;
    }
    this.plugins.load(this.plugin, this.findEditor().modulePath).then((m) => {
      this.editor = new m.default(this.plugins.getApi(this.plugin)) as EditorPlugin;

      this.dialog
        .message({
          header: `Edit ${this.descriptor.name}`,
          template: popup,
          validation: () => this.valid,
          actions: [ACTION_CANCEL, ACTION_OK],
        })
        .pipe(finalize(() => (this.editor = null)))
        .subscribe((v) => {
          if (v) {
            this.valueConfirmed.emit(this.currentValue);
          }
        });
    });
  }

  private findEditor(): CustomEditor {
    return this.plugin.editors.find((e) => e.typeName === this.descriptor.customEditor);
  }
}
