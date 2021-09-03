import { Component, forwardRef, Inject, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { NgForm } from '@angular/forms';
import { BdDataColumn } from 'src/app/models/data';
import { CustomAttributeDescriptor } from 'src/app/models/gen.dtos';
import { BdDataTableComponent } from 'src/app/modules/core/components/bd-data-table/bd-data-table.component';
import { ACTION_APPLY, ACTION_CANCEL, ACTION_OK } from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { SettingsGeneralComponent } from '../settings-general.component';

@Component({
  selector: 'app-attributes-tab',
  templateUrl: './attributes-tab.component.html',
  styleUrls: ['./attributes-tab.component.css'],
})
export class AttributesTabComponent implements OnInit {
  private defIdCol: BdDataColumn<CustomAttributeDescriptor> = {
    id: 'id',
    name: 'ID',
    data: (r) => r.name,
  };

  private defDescCol: BdDataColumn<CustomAttributeDescriptor> = {
    id: 'desc',
    name: 'Description',
    data: (r) => r.description,
  };

  private defEditCol: BdDataColumn<CustomAttributeDescriptor> = {
    id: 'edit',
    name: 'Edit',
    data: (r) => `Edit definition ${r.name}`,
    action: (r) => this.editAttribute(r),
    icon: (r) => 'edit',
    width: '40px',
  };

  private defDelCol: BdDataColumn<CustomAttributeDescriptor> = {
    id: 'delete',
    name: 'Rem.',
    data: (r) => `Remove definition ${r.name}`,
    action: (r) => this.removeAttribute(r),
    icon: (r) => 'delete',
    width: '40px',
  };

  @ViewChild('attributeTable') private attributeTable: BdDataTableComponent<CustomAttributeDescriptor>;
  @ViewChild('addEditDialog') private addEditDialog: TemplateRef<any>;
  @ViewChild('addEditForm', { static: false }) addEditForm: NgForm;

  /* template */ attributeColumns: BdDataColumn<CustomAttributeDescriptor>[] = [this.defIdCol, this.defDescCol, this.defEditCol, this.defDelCol];
  /* template */ tempAttribute: CustomAttributeDescriptor;

  constructor(public settings: SettingsService, @Inject(forwardRef(() => SettingsGeneralComponent)) private parent: SettingsGeneralComponent) {}

  ngOnInit(): void {}

  private removeAttribute(attr: CustomAttributeDescriptor): void {
    this.settings.settings$.value.instanceGroup.attributes.splice(this.settings.settings$.value.instanceGroup.attributes.indexOf(attr), 1);
    this.attributeTable.update();
  }

  /* template */ addAttribute(): void {
    this.tempAttribute = { name: '', description: '' };
    this.parent.dialog
      .message({
        header: 'Add Attribute',
        template: this.addEditDialog,
        actions: [ACTION_CANCEL, ACTION_OK],
        validation: () => !this.addEditForm || this.addEditForm.valid,
      })
      .subscribe((r) => {
        if (r) {
          this.settings.settings$.value.instanceGroup.attributes.push(this.tempAttribute);
          this.attributeTable.update();
        }
      });
  }

  private editAttribute(attr: CustomAttributeDescriptor): void {
    this.tempAttribute = { ...attr };
    this.parent.dialog
      .message({
        header: 'Edit Attribute',
        template: this.addEditDialog,
        actions: [ACTION_CANCEL, ACTION_APPLY],
        validation: () => !this.addEditForm || this.addEditForm.valid,
      })
      .subscribe((r) => {
        if (r) {
          attr.name = this.tempAttribute.name;
          attr.description = this.tempAttribute.description;
          this.attributeTable.update();
        }
      });
  }
}
