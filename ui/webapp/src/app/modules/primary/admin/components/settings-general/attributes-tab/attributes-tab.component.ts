import { Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { Subscription } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { CustomAttributeDescriptor } from 'src/app/models/gen.dtos';
import { BdDataTableComponent } from 'src/app/modules/core/components/bd-data-table/bd-data-table.component';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { SettingsService } from 'src/app/modules/core/services/settings.service';
import { AttributeEditActionComponent } from './attribute-edit-action/attribute-edit-action.component';

@Component({
  selector: 'app-attributes-tab',
  templateUrl: './attributes-tab.component.html',
})
export class AttributesTabComponent implements OnInit, OnDestroy {
  private areas = inject(NavAreasService);
  protected settings = inject(SettingsService);

  private defIdCol: BdDataColumn<CustomAttributeDescriptor> = {
    id: 'id',
    name: 'ID',
    data: (r) => r.name,
    isId: true,
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
    component: AttributeEditActionComponent,
    icon: () => 'edit',
    width: '40px',
  };

  private defDelCol: BdDataColumn<CustomAttributeDescriptor> = {
    id: 'delete',
    name: 'Rem.',
    data: (r) => `Remove definition ${r.name}`,
    action: (r) => this.settings.removeAttribute(r),
    icon: () => 'delete',
    width: '40px',
    actionDisabled: (r) => this.selectedAttributeName === r.name,
  };

  protected attributeColumns: BdDataColumn<CustomAttributeDescriptor>[] = [
    this.defIdCol,
    this.defDescCol,
    this.defEditCol,
    this.defDelCol,
  ];
  protected tempAttribute: CustomAttributeDescriptor;
  protected tempUsedIds: string[];
  private selectedAttributeName: string;

  private subscription: Subscription;

  @ViewChild(BdDataTableComponent) private table: BdDataTableComponent<CustomAttributeDescriptor>;

  ngOnInit(): void {
    this.subscription = this.areas.panelRoute$.subscribe((route) => {
      if (!route?.params || !route.params['attribute']) {
        this.selectedAttributeName = null;
        return;
      }
      this.selectedAttributeName = route.params['attribute'];
    });
    this.subscription.add(this.settings.settingsUpdated$.subscribe(() => this.table?.update()));
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }
}
