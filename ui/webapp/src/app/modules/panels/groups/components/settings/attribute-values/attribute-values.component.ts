import { Component, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { BehaviorSubject, combineLatest } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { BdDataColumn } from 'src/app/models/data';
import { CustomAttributeDescriptor, CustomAttributesRecord, InstanceGroupConfiguration } from 'src/app/models/gen.dtos';
import { ACTION_APPLY, ACTION_CANCEL } from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { GroupDetailsService } from '../../../services/group-details.service';

interface AttributeRow {
  id: string;
  name: string;
  value: string;
}

@Component({
  selector: 'app-attribute-values',
  templateUrl: './attribute-values.component.html',
  styleUrls: ['./attribute-values.component.css'],
})
export class AttributeValuesComponent implements OnInit {
  private attrNameCol: BdDataColumn<AttributeRow> = {
    id: 'attribute',
    name: 'Attribute',
    data: (r) => r.name,
  };

  private attrValCol: BdDataColumn<AttributeRow> = {
    id: 'value',
    name: 'Value',
    data: (r) => r.value,
  };

  private attrRemoveCol: BdDataColumn<AttributeRow> = {
    id: 'remove',
    name: 'Rem.',
    data: (r) => `Remove value for ${r.name}`,
    icon: (r) => 'delete',
    action: (r) => this.removeAttribute(r),
    width: '30px',
  };

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  /* template */ loading$ = new BehaviorSubject<boolean>(false);
  /* template */ columns: BdDataColumn<AttributeRow>[] = [this.attrNameCol, this.attrValCol, this.attrRemoveCol];
  /* template */ records: AttributeRow[] = [];
  /* template */ defs: CustomAttributeDescriptor[];

  /* template */ newAttr: CustomAttributeDescriptor;
  /* template */ newValue: string;
  /* template */ defLabels: string[];

  private group: InstanceGroupConfiguration;
  private attributes: CustomAttributesRecord;

  constructor(private groups: GroupsService, private details: GroupDetailsService) {}

  ngOnInit(): void {
    combineLatest([this.groups.current$, this.groups.attributeDefinitions$, this.groups.currentAttributeValues$]).subscribe((r) => {
      this.group = r[0];
      this.defs = r[1];
      this.attributes = r[2];
      this.defLabels = this.defs.map((d) => d.description);

      // if we have values for both
      this.createRows();
    });
  }

  /* template */ showAddDialog(template: TemplateRef<any>) {
    this.dialog
      .message({
        header: 'Set Attribute Value',
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
        this.details
          .updateAttributes(this.group.name, this.attributes)
          .pipe(finalize(() => this.loading$.next(false)))
          .subscribe();
      });
  }

  private removeAttribute(r: AttributeRow) {
    delete this.attributes.attributes[r.id];
    this.loading$.next(true);
    this.details
      .updateAttributes(this.group.name, this.attributes)
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
        if (!!this.attributes.attributes[def.name]) {
          result.push({ id: def.name, name: def.description, value: this.attributes.attributes[def.name] });
        }
      }
      this.records = result;
    }
  }
}
