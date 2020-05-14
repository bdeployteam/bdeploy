import { Component, ElementRef, EventEmitter, Input, OnInit, Output, TemplateRef, ViewChild } from '@angular/core';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { ManifestKey, ParameterConfiguration, ParameterDescriptor, PluginInfoDto } from 'src/app/models/gen.dtos';
import { EditorPlugin, PluginService } from 'src/app/modules/core/services/plugin.service';

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

  @ViewChild('editorPanel', {static: false})
  editorPanel: ElementRef<any>;

  plugin: PluginInfoDto;

  currentValue: any;
  valid = false;
  private dialogRef: MatDialogRef<any>;

  constructor(private plugins: PluginService, private dialog: MatDialog) { }

  ngOnInit(): void {
    this.plugins.getEditorPlugin(this.instanceGroup, this.product, this.descriptor.customEditor).subscribe(r => this.plugin = r);
  }

  showEditor(popup: TemplateRef<any>) {
    this.plugins.load(this.plugin, this.plugin.editors.find(e => e.typeName === this.descriptor.customEditor)).then(m => {
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

}
