import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, EventEmitter, Input, OnInit, Output, TemplateRef, ViewChild } from '@angular/core';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { tap } from 'rxjs/operators';
import { CustomEditor, ManifestKey, ParameterConfiguration, ParameterDescriptor, PluginInfoDto } from 'src/app/models/gen.dtos';
import { PluginService } from 'src/app/modules/core/services/plugin.service';
import { EditorPlugin } from 'src/app/modules/shared/plugins/plugin.editor';

@Component({
  selector: 'app-custom-editor',
  templateUrl: './custom-editor.component.html',
  styleUrls: ['./custom-editor.component.css']
})
export class CustomEditorComponent implements OnInit {

  @Input()
  instanceGroup: string;

  @Input()
  product: ManifestKey;

  @Input()
  descriptor: ParameterDescriptor;

  @Input()
  value: ParameterConfiguration;

  @Output()
  valueConfirmed: EventEmitter<any> = new EventEmitter<any>();

  @Output()
  pluginLoaded: EventEmitter<CustomEditor> = new EventEmitter<CustomEditor>();

  @ViewChild('editorPanel', {static: false})
  editorPanel: ElementRef<any>;

  plugin: PluginInfoDto;
  error: HttpErrorResponse;

  currentValue: any;
  valid = false;
  private dialogRef: MatDialogRef<any>;

  constructor(private plugins: PluginService, private dialog: MatDialog) { }

  ngOnInit(): void {
    this.plugins.getEditorPlugin(this.instanceGroup, this.product, this.descriptor.customEditor).pipe(tap(n => {}, e => {
      console.log('Cannot load custom editor for type ' + this.descriptor.customEditor, e);
      if (e instanceof HttpErrorResponse) {
        this.error = e;
      }
    })).subscribe(r => {
      this.plugin = r;
      this.pluginLoaded.emit(this.findEditor());
    });
  }

  showEditor(popup: TemplateRef<any>) {
    this.plugins.load(this.plugin, this.findEditor()).then(m => {
      const editor = new m.default(this.plugins.getApi(this.plugin)) as EditorPlugin;

      this.dialogRef = this.dialog.open(popup, {
        width: '600px'
      });
      this.dialogRef.afterOpened().subscribe(() => {
        this.editorPanel.nativeElement.appendChild(editor.bind(() => this.value.value, (v) => this.currentValue = v, (s) => this.valid = s));
      });
      this.dialogRef.afterClosed().subscribe(v => {
        if (v) {
          this.valueConfirmed.emit(v);
        }
        this.dialogRef = null;
      });

    });
  }

  private findEditor(): CustomEditor {
    return this.plugin.editors.find(e => e.typeName === this.descriptor.customEditor);
  }
}
