import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Sort } from '@angular/material/sort';
import { BehaviorSubject, finalize, Subscription } from 'rxjs';
import {
  BdDataColumn,
  BdDataGrouping,
  BdDataGroupingDefinition,
} from 'src/app/models/data';
import { InstanceDto, InstanceOverallStatusDto } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { CardViewService } from 'src/app/modules/core/services/card-view.service';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstanceBulkService } from 'src/app/modules/panels/instances/services/instance-bulk.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { GroupsService } from '../../../groups/services/groups.service';
import { InstancesColumnsService } from '../../services/instances-columns.service';
import { InstancesService } from '../../services/instances.service';
import { OverallStatusColumnComponent } from './overall-status-column/overall-status-column.component';

@Component({
  selector: 'app-instances-browser',
  templateUrl: './browser.component.html',
})
export class InstancesBrowserComponent implements OnInit, OnDestroy {
  initGrouping: BdDataGroupingDefinition<InstanceDto>[] = [
    { name: 'Instance Purpose', group: (r) => r.instanceConfiguration.purpose },
    { name: 'Product', group: (r) => r.productDto.name },
  ];
  grouping: BdDataGroupingDefinition<InstanceDto>[];
  defaultGrouping: BdDataGrouping<InstanceDto> = {
    definition: this.initGrouping[0],
    selected: [],
  };
  hasProducts$ = new BehaviorSubject<boolean>(false);
  syncingAll$ = new BehaviorSubject<boolean>(false);

  private overallStates: InstanceOverallStatusDto[];
  private subscription: Subscription;

  private colOverallStatus: BdDataColumn<InstanceDto> = {
    id: 'status',
    name: 'Status',
    data: (r) =>
      this.overallStates?.find((x) => x.uuid === r.instanceConfiguration?.uuid),
    component: OverallStatusColumnComponent,
    width: '50px',
  };

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

  /* template */ getRecordRoute = (row: InstanceDto) => {
    return [
      '/instances',
      'dashboard',
      this.areas.groupContext$.value,
      row.instanceConfiguration.uuid,
    ];
  };

  /* template */ isCardView: boolean;
  /* template */ presetKeyValue = 'instances';
  /* template */ sort: Sort = { active: 'name', direction: 'asc' };
  /* template */ columns: BdDataColumn<InstanceDto>[] = [
    this.instanceColumns.instanceNameColumn,
    this.instanceColumns.instanceTypeColumn,
    this.instanceColumns.instanceIdColumn,
    this.instanceColumns.instanceDescriptionColumn,
    this.instanceColumns.instanceProductColumn,
    this.instanceColumns.instanceProductVersionColumn,
    this.instanceColumns.instanceProductActiveColumn,
    this.instanceColumns.instanceBannerColumn,
    this.instanceColumns.instanceServerColumn,
    this.colOverallStatus,
    this.instanceColumns.instanceSyncColumn,
  ];

  constructor(
    public instances: InstancesService,
    public instanceColumns: InstancesColumnsService,
    public products: ProductsService,
    public groups: GroupsService,
    public areas: NavAreasService,
    public authService: AuthenticationService,
    public bulk: InstanceBulkService,
    config: ConfigService,
    private cardViewService: CardViewService
  ) {
    this.subscription = config.isCentral$.subscribe((value) => {
      if (value) {
        this.initGrouping.push({
          name: 'Managed Server',
          group: (r) => r.managedServer.hostName,
        });
      }
      this.grouping = [...this.initGrouping];
    });
  }

  ngOnInit(): void {
    this.isCardView = this.cardViewService.checkCardView(this.presetKeyValue);
    this.subscription.add(
      this.products.products$.subscribe((p) =>
        this.hasProducts$.next(!!p && !!p.length)
      )
    );
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
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  doSyncAll() {
    this.dialog
      .confirm(
        'Query all Instances',
        'This action will <strong>contact all servers and nodes</strong> in the group, to fetch the latest data. This may take a while. Are you sure?'
      )
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.syncingAll$.next(true);
        this.instances
          .syncAndFetchState()
          .pipe(finalize(() => this.syncingAll$.next(false)))
          .subscribe((s) => {
            this.overallStates = s;
          });
      });
  }
}
