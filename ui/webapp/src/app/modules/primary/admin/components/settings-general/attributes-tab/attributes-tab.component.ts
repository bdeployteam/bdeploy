import { Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Subscription } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { CustomAttributeDescriptor } from 'src/app/models/gen.dtos';
import { BdDataTableComponent } from 'src/app/modules/core/components/bd-data-table/bd-data-table.component';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { AttributeDeleteActionComponent } from './attribute-delete-action/attribute-delete-action.component';
import { AttributeEditActionComponent } from './attribute-edit-action/attribute-edit-action.component';

import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-attributes-tab',
    templateUrl: './attributes-tab.component.html',
  imports: [BdDataTableComponent, AsyncPipe]
})
export class AttributesTabComponent implements OnInit, OnDestroy {
  protected readonly settings = inject(SettingsService);

  private readonly defIdCol: BdDataColumn<CustomAttributeDescriptor> = {
    id: 'name',
    name: 'Name',
    data: (r) => r.name,
    isId: true,
  };

  private readonly defDescCol: BdDataColumn<CustomAttributeDescriptor> = {
    id: 'desc',
    name: 'Description',
    data: (r) => r.description,
  };

  private readonly defEditCol: BdDataColumn<CustomAttributeDescriptor> = {
    id: 'edit',
    name: 'Edit',
    data: (r) => `Edit attribute definition ${r.name}`,
    component: AttributeEditActionComponent,
    icon: () => 'edit',
    width: '40px',
  };

  private readonly defDelCol: BdDataColumn<CustomAttributeDescriptor> = {
    id: 'delete',
    name: 'Rem.',
    data: (r) => `Remove attribute definition ${r.name}`,
    component: AttributeDeleteActionComponent,
    icon: () => 'delete',
    width: '40px',
  };

  protected readonly attributeColumns: BdDataColumn<CustomAttributeDescriptor>[] = [
    this.defIdCol,
    this.defDescCol,
    this.defEditCol,
    this.defDelCol,
  ];
  protected tempAttribute: CustomAttributeDescriptor;
  protected tempUsedIds: string[];

  private subscription: Subscription;

  @ViewChild(BdDataTableComponent) private readonly table: BdDataTableComponent<CustomAttributeDescriptor>;

  ngOnInit(): void {
    this.subscription = this.settings.settingsUpdated$.subscribe(() => this.table?.update());
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }
}
