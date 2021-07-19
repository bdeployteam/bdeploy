import { Component, OnDestroy, OnInit } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { BdDataGrouping, BdDataGroupingDefinition } from 'src/app/models/data';
import { InstanceDto } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstanceBulkService } from 'src/app/modules/panels/instances/services/instance-bulk.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { GroupsService } from '../../../groups/services/groups.service';
import { InstancesColumnsService } from '../../services/instances-columns.service';
import { InstancesService } from '../../services/instances.service';

@Component({
  selector: 'app-instances-browser',
  templateUrl: './browser.component.html',
  styleUrls: ['./browser.component.css'],
})
export class InstancesBrowserComponent implements OnInit, OnDestroy {
  initGrouping: BdDataGroupingDefinition<InstanceDto>[] = [
    { name: 'Instance Purpose', group: (r) => r.instanceConfiguration.purpose },
    { name: 'Product', group: (r) => r.productDto.name },
  ];
  grouping: BdDataGroupingDefinition<InstanceDto>[];
  defaultGrouping: BdDataGrouping<InstanceDto> = { definition: this.initGrouping[0], selected: [] };
  hasProducts$ = new BehaviorSubject<boolean>(false);

  private subscription: Subscription;

  /* template */ getRecordRoute = (row: InstanceDto) => {
    return ['/instances', 'dashboard', this.areas.groupContext$.value, row.instanceConfiguration.uuid];
  };

  constructor(
    public instances: InstancesService,
    public instanceColumns: InstancesColumnsService,
    public products: ProductsService,
    public groups: GroupsService,
    public areas: NavAreasService,
    public authService: AuthenticationService,
    public bulk: InstanceBulkService,
    config: ConfigService
  ) {
    if (config.isCentral()) {
      this.initGrouping.push({ name: 'Managed Server', group: (r) => r.managedServer.hostName });
    }
    this.grouping = [...this.initGrouping];
  }

  ngOnInit(): void {
    this.subscription = this.products.products$.subscribe((p) => this.hasProducts$.next(!!p && !!p.length));
    this.subscription.add(
      this.groups.current$.subscribe((g) => {
        if (!g) {
          return;
        }
        this.grouping = [...this.initGrouping];
        for (const attr of g.instanceAttributes) {
          this.grouping.push({ name: attr.description, group: (r) => r.attributes.attributes[attr.name] });
        }
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ isAddAllowed(): boolean {
    return this.authService.isCurrentScopeWrite();
  }
}
