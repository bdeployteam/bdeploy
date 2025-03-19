import { Component, inject, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { BehaviorSubject, combineLatest } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { BdDataColumn } from 'src/app/models/data';
import { CustomAttributeDescriptor, CustomAttributesRecord, InstanceDto } from 'src/app/models/gen.dtos';
import {
  ACTION_APPLY,
  ACTION_CANCEL
} from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { BdFormSelectComponent } from '../../../../../core/components/bd-form-select/bd-form-select.component';
import { FormsModule } from '@angular/forms';
import { BdFormInputComponent } from '../../../../../core/components/bd-form-input/bd-form-input.component';

import { BdDialogToolbarComponent } from '../../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdDataDisplayComponent } from '../../../../../core/components/bd-data-display/bd-data-display.component';
import { BdNoDataComponent } from '../../../../../core/components/bd-no-data/bd-no-data.component';
import { BdButtonComponent } from '../../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';

interface AttributeRow {
  id: string;
  name: string;
  value: string;
}

@Component({
    selector: 'app-attributes',
    templateUrl: './attributes.component.html',
  imports: [BdFormSelectComponent, FormsModule, BdFormInputComponent, BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, BdDataDisplayComponent, BdNoDataComponent, BdButtonComponent, AsyncPipe]
})
export class AttributesComponent implements OnInit {
  private readonly groups = inject(GroupsService);
  private readonly instances = inject(InstancesService);
  protected readonly servers = inject(ServersService);

  private readonly attrNameCol: BdDataColumn<AttributeRow, string> = {
    id: 'attribute',
    name: 'Attribute',
    data: (r) => r.name,
    isId: true,
  };

  private readonly attrValCol: BdDataColumn<AttributeRow, string> = {
    id: 'value',
    name: 'Value',
    data: (r) => r.value,
  };

  private readonly attrRemoveCol: BdDataColumn<AttributeRow, string> = {
    id: 'remove',
    name: 'Rem.',
    data: (r) => `Remove value for ${r.name}`,
    icon: () => 'delete',
    action: (r) => this.removeAttribute(r),
    width: '30px',
  };

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  protected loading$ = new BehaviorSubject<boolean>(false);
  protected readonly columns: BdDataColumn<AttributeRow, unknown>[] = [this.attrNameCol, this.attrValCol, this.attrRemoveCol];
  protected records: AttributeRow[] = [];
  protected defs: CustomAttributeDescriptor[];

  protected newAttr: CustomAttributeDescriptor;
  protected newValue: string;
  protected instance: InstanceDto;
  protected defLabels: string[];

  private attributes: CustomAttributesRecord;

  ngOnInit(): void {
    combineLatest([this.groups.current$, this.instances.current$]).subscribe(([group, instance]) => {
      if (!group || !instance) {
        return;
      }

      this.instance = instance;
      this.defs = group.instanceAttributes;
      this.attributes = instance.attributes;
      this.defLabels = this.defs?.map((d) => d.description);

      // if we have values for both
      this.createRows();
    });
  }

  protected showAddDialog(template: TemplateRef<unknown>) {
    this.dialog
      .message({
        header: 'Add/Edit Attribute Value',
        icon: 'edit',
        template: template,
        validation: () => !!this.newAttr && !!this.newValue?.length,
        actions: [ACTION_CANCEL, ACTION_APPLY],
      })
      .subscribe((r) => {
        const attr = this.newAttr;
        const value = this.newValue;
        this.newAttr = this.newValue = null;

        if (!r) {
          return;
        }

        this.attributes.attributes[attr.name] = value;

        this.loading$.next(true);
        this.instances
          .updateAttributes(this.instance.instanceConfiguration.id, this.attributes)
          .pipe(finalize(() => this.loading$.next(false)))
          .subscribe();
      });
  }

  private removeAttribute(r: AttributeRow) {
    delete this.attributes.attributes[r.id];
    this.loading$.next(true);
    this.instances
      .updateAttributes(this.instance.instanceConfiguration.id, this.attributes)
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe();
  }

  private createRows() {
    if (!this.attributes?.attributes) {
      this.attributes = { attributes: {} };
    }

    if (!this.defs?.length) {
      this.records = [];
    } else {
      const result: AttributeRow[] = [];
      for (const def of this.defs) {
        if (this.attributes.attributes[def.name]) {
          result.push({
            id: def.name,
            name: def.description,
            value: this.attributes.attributes[def.name],
          });
        }
      }
      this.records = result;
    }
  }
}
