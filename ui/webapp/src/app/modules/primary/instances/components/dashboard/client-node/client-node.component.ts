import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { BdDataGrouping } from 'src/app/models/data';
import { ApplicationConfiguration, InstanceNodeConfigurationDto } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { ClientsService } from 'src/app/modules/primary/groups/services/clients.service';
import { InstancesService } from '../../../services/instances.service';
import { ProcessesColumnsService } from '../../../services/processes-columns.service';
import { StateItem } from '../state-panel/state-panel.component';

@Component({
  selector: 'app-instance-client-node',
  templateUrl: './client-node.component.html',
  styleUrls: ['./client-node.component.css'],
})
export class ClientNodeComponent implements OnInit, OnDestroy {
  @Input() node: InstanceNodeConfigurationDto;

  @Input() gridWhen$: BehaviorSubject<boolean>;
  @Input() groupingWhen$: BehaviorSubject<BdDataGrouping<ApplicationConfiguration>[]>;
  @Input() collapsedWhen$: BehaviorSubject<boolean>;
  @Input() narrowWhen$: BehaviorSubject<boolean>;

  /* template */ nodeStateItems$ = new BehaviorSubject<StateItem[]>([]);
  /* template */ columns = this.appCols.defaultProcessClientColumns;

  /* template */ getRecordRoute = (row: ApplicationConfiguration) => {
    return ['', { outlets: { panel: ['panels', 'groups', 'client', row.uid] } }];
  };

  private subscription: Subscription;

  constructor(
    public instances: InstancesService,
    public clients: ClientsService,
    private appCols: ProcessesColumnsService,
    private areas: NavAreasService,
    private auth: AuthenticationService
  ) {}

  ngOnInit(): void {
    this.subscription = this.instances.activeNodeStates$.subscribe((states) => {
      // actually not needed, we just use the same info source as server nodes to have info appear at the same time.
      if (!states) {
        this.nodeStateItems$.next([]);
        return;
      }

      const updAvail = !!this.instances.current$.value?.newerVersionAvailable;
      const prodItem: StateItem = {
        name: this.node.nodeConfiguration.product.tag,
        type: updAvail ? 'update' : 'product',
        tooltip: `Product Version: ${this.node.nodeConfiguration.product.tag}${updAvail ? ' - Newer version available' : ''}`,
        click:
          updAvail && this.auth.isCurrentScopeWrite()
            ? () => {
                this.areas.navigateBoth(
                  ['instances', 'configuration', this.areas.groupContext$.value, this.node.nodeConfiguration.uuid],
                  ['panels', 'instances', 'settings', 'product']
                );
              }
            : null,
      };

      this.nodeStateItems$.next([prodItem]);
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
