import { Component, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { BehaviorSubject, combineLatest } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { BdDataColumn } from 'src/app/models/data';
import {
  CustomAttributeDescriptor,
  CustomAttributesRecord,
  InstanceDto,
} from 'src/app/models/gen.dtos';
import {
  ACTION_APPLY,
  ACTION_CANCEL,
} from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';

interface AttributeRow {
  id: string;
  name: string;
  value: string;
}

@Component({
  selector: 'app-attributes',
  templateUrl: './attributes.component.html',
})
export class AttributesComponent implements OnInit {
  private attrNameCol: BdDataColumn<AttributeRow> = {
    id: 'attribute',
    name: 'Attribute',
    data: (r) => r.name,
    isId: true,
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
    icon: () => 'delete',
    action: (r) => this.removeAttribute(r),
    width: '30px',
  };

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  /* template */ loading$ = new BehaviorSubject<boolean>(false);
  /* template */ columns: BdDataColumn<AttributeRow>[] = [
    this.attrNameCol,
    this.attrValCol,
    this.attrRemoveCol,
  ];
  /* template */ records: AttributeRow[] = [];
  /* template */ defs: CustomAttributeDescriptor[];

  /* template */ newAttr: CustomAttributeDescriptor;
  /* template */ newValue: string;
  /* template */ instance: InstanceDto;
  /* template */ defLabels: string[];

  private attributes: CustomAttributesRecord;

  constructor(
    private groups: GroupsService,
    private instances: InstancesService,
    public servers: ServersService
  ) {}

  ngOnInit(): void {
    combineLatest([this.groups.current$, this.instances.current$]).subscribe(
      ([group, instance]) => {
        if (!group || !instance) {
          return;
        }

        this.instance = instance;
        this.defs = group.instanceAttributes;
        this.attributes = instance.attributes;
        this.defLabels = this.defs?.map((d) => d.description);

        // if we have values for both
        this.createRows();
      }
    );
  }

  /* template */ showAddDialog(template: TemplateRef<any>) {
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
          .updateAttributes(
            this.instance.instanceConfiguration.id,
            this.attributes
          )
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
