import { Component, OnDestroy, OnInit } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { BdDataGroupingDefinition } from 'src/app/models/data';
import { InstanceDto } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { GroupsService } from '../../../groups/services/groups.service';
import { InstancesColumnsService } from '../../services/instances-columns.service';
import { InstancesService } from '../../services/instances.service';

@Component({
  selector: 'app-instances-browser',
  templateUrl: './instances-browser.component.html',
  styleUrls: ['./instances-browser.component.css'],
})
export class InstancesBrowserComponent implements OnInit, OnDestroy {
  grouping: BdDataGroupingDefinition<InstanceDto>[] = [];
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
    private authService: AuthenticationService
  ) {}

  ngOnInit(): void {
    this.subscription = this.products.products$.subscribe((p) => this.hasProducts$.next(!!p && !!p.length));
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ isAddAllowed(): boolean {
    return this.authService.isCurrentScopeWrite();
  }
}
