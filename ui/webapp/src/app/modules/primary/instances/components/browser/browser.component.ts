import { Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { Sort } from '@angular/material/sort';
import { BehaviorSubject, Subscription } from 'rxjs';
import { BdDataColumn, BdDataGrouping, BdDataGroupingDefinition } from 'src/app/models/data';
import { CustomDataGrouping, InstanceDto, InstanceGroupConfiguration } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { CardViewService } from 'src/app/modules/core/services/card-view.service';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { calculateGrouping } from 'src/app/modules/core/utils/preset.utils';
import { InstanceBulkService } from 'src/app/modules/panels/instances/services/instance-bulk.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { GroupsService } from '../../../groups/services/groups.service';
import { InstancesColumnsService } from '../../services/instances-columns.service';
import { InstancesService } from '../../services/instances.service';
import { BdDataDisplayComponent } from './../../../../core/components/bd-data-display/bd-data-display.component';
import { OverallStatusColumnComponent } from './overall-status-column/overall-status-column.component';

@Component({
  selector: 'app-instances-browser',
  templateUrl: './browser.component.html',
})
export class InstancesBrowserComponent implements OnInit, OnDestroy {
  private config = inject(ConfigService);
  private cardViewService = inject(CardViewService);
  protected instances = inject(InstancesService);
  protected instanceColumns = inject(InstancesColumnsService);
  protected products = inject(ProductsService);
  protected groups = inject(GroupsService);
  protected areas = inject(NavAreasService);
  protected authService = inject(AuthenticationService);
  protected bulk = inject(InstanceBulkService);

  protected initGrouping: BdDataGroupingDefinition<InstanceDto>[] = [
    {
      name: 'System',
      group: (r) => this.instanceColumns.instanceSystemColumn.data(r),
      associatedColumn: this.instanceColumns.instanceSystemColumn.id,
    },
    {
      name: 'Instance Purpose',
      group: (r) => r.instanceConfiguration.purpose,
      associatedColumn: this.instanceColumns.instanceTypeColumn.id,
    },
    {
      name: 'Product',
      group: (r) =>
        this.products.products$.value.find(
          (p) =>
            p.key.name === r.instanceConfiguration.product.name && p.key.tag === r.instanceConfiguration.product.tag
        )?.name || r.instanceConfiguration.product.name,
      associatedColumn: this.instanceColumns.instanceProductColumn.id,
    },
  ];
  protected grouping: BdDataGroupingDefinition<InstanceDto>[];
  protected hasProducts$ = new BehaviorSubject<boolean>(false);

  private defaultSingleGrouping: BdDataGrouping<InstanceDto>[] = [{ definition: this.initGrouping[0], selected: [] }];
  private defaultMultipleGrouping: BdDataGrouping<InstanceDto>[] = [{ definition: this.initGrouping[0], selected: [] }];

  private subscription: Subscription;

  private colOverallStatus: BdDataColumn<InstanceDto> = {
    id: 'status',
    name: 'Status',
    description: 'Status',
    data: (r) => this.instances.overallStates$.value?.find((x) => x.id === r.instanceConfiguration?.id),
    component: OverallStatusColumnComponent,
    width: '110px', // required width in case timestamp is shown along with the icon.
  };

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;
  @ViewChild(BdDataDisplayComponent) private data: BdDataDisplayComponent<InstanceDto>;

  protected getRecordRoute = (row: InstanceDto) => {
    return ['/instances', 'dashboard', this.areas.groupContext$.value, row.instanceConfiguration.id];
  };

  protected isCardView: boolean;
  protected sort: Sort = { active: 'name', direction: 'asc' };
  protected columns: BdDataColumn<InstanceDto>[] = [
    this.instanceColumns.instanceNameColumn,
    this.instanceColumns.instanceIdColumn,
    this.instanceColumns.instanceDescriptionColumn,
    this.instanceColumns.instanceSystemColumn,
    this.instanceColumns.instanceProductColumn,
    this.instanceColumns.instanceProductVersionColumn,
    this.instanceColumns.instanceProductActiveColumn,
    this.instanceColumns.instanceServerColumn,
    this.instanceColumns.instanceTypeColumn,
    this.instanceColumns.instanceBannerColumn,
    this.colOverallStatus,
    this.instanceColumns.instanceSyncColumn,
  ];

  ngOnInit(): void {
    this.subscription = this.config.isCentral$.subscribe((value) => {
      if (value) {
        this.initGrouping.push({
          name: 'Managed Server',
          group: (r) => r.managedServer.hostName,
          associatedColumn: this.instanceColumns.instanceServerColumn.id,
        });
      }
      this.grouping = [...this.initGrouping];
    });

    this.isCardView = this.cardViewService.checkCardView(this.presetKeyValue);
    this.subscription.add(this.products.products$.subscribe((p) => this.hasProducts$.next(!!p && !!p.length)));
    this.subscription.add(
      this.groups.current$.subscribe((g) => {
        if (!g) {
          return;
        }

        this.grouping = [...this.initGrouping];
        for (const attr of g.instanceAttributes) {
          this.grouping.push({
            name: attr.description,
            group: (r) => r.attributes.attributes[attr.name],
          });
        }

        this.calculateDefaultGrouping(g);
      })
    );

    this.subscription.add(this.instances.overallStates$.subscribe(() => this.data?.redraw()));
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  private calculateDefaultGrouping(g: InstanceGroupConfiguration): void {
    this.defaultSingleGrouping =
      g.groupingSinglePreset === null
        ? this.generateDefaultGrouping()
        : calculateGrouping(this.grouping, g.groupingSinglePreset);
    this.defaultMultipleGrouping =
      g.groupingMultiplePreset === null
        ? this.generateDefaultGrouping()
        : calculateGrouping(this.grouping, g.groupingMultiplePreset);
  }

  private generateDefaultGrouping(): BdDataGrouping<InstanceDto>[] {
    return [
      {
        definition: this.initGrouping[0],
        selected: [],
      },
    ];
  }

  protected get presetKeyValue(): string {
    return `instance-${this.groups.current$.value?.name}`;
  }

  protected get defaultGrouping(): BdDataGrouping<InstanceDto>[] {
    return this.isCardView ? this.defaultSingleGrouping : this.defaultMultipleGrouping;
  }

  protected saveGlobalPreset(preset: CustomDataGrouping[]) {
    const group = this.groups.current$.value;
    const multiple = !this.isCardView;
    this.groups.updatePreset(group.name, preset, multiple).subscribe();
    // reset global preset immediately for smoother ux
    if (multiple) {
      group.groupingMultiplePreset = preset;
    } else {
      group.groupingSinglePreset = preset;
    }
    this.calculateDefaultGrouping(group);
  }

  protected doSyncAll() {
    this.dialog
      .confirm(
        'Query all Instances',
        'This action will <strong>contact all servers and nodes</strong> in the group, to fetch the latest data. This may take a while. Are you sure?'
      )
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.instances.syncAndFetchState([]);
      });
  }
}
