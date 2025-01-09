import { Component, Input, OnDestroy, OnInit, inject } from '@angular/core';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { BdDataGrouping } from 'src/app/models/data';
import { ApplicationConfiguration, InstanceNodeConfigurationDto } from 'src/app/models/gen.dtos';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { CardViewService } from 'src/app/modules/core/services/card-view.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstancesService } from '../../../services/instances.service';
import { ProcessesColumnsService } from '../../../services/processes-columns.service';
import { StateItem, NodeStatePanelComponent } from '../state-panel/state-panel.component';
import { MatDivider } from '@angular/material/divider';
import { BdDataDisplayComponent } from '../../../../../core/components/bd-data-display/bd-data-display.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-instance-client-node',
    templateUrl: './client-node.component.html',
    styleUrls: ['./client-node.component.css'],
    imports: [NodeStatePanelComponent, MatDivider, BdDataDisplayComponent, AsyncPipe]
})
export class ClientNodeComponent implements OnInit, OnDestroy {
  private readonly appCols = inject(ProcessesColumnsService);
  private readonly areas = inject(NavAreasService);
  private readonly auth = inject(AuthenticationService);
  private readonly cardViewService = inject(CardViewService);
  protected readonly instances = inject(InstancesService);

  @Input() node: InstanceNodeConfigurationDto;

  @Input() gridWhen$: BehaviorSubject<boolean>;
  @Input() groupingWhen$: BehaviorSubject<BdDataGrouping<ApplicationConfiguration>[]>;
  @Input() collapsedWhen$: BehaviorSubject<boolean>;
  @Input() narrowWhen$: BehaviorSubject<boolean>;

  protected nodeStateItems$ = new BehaviorSubject<StateItem[]>([]);
  protected columns = this.appCols.defaultProcessClientColumns;

  protected getRecordRoute = (row: ApplicationConfiguration) => [
    '',
    { outlets: { panel: ['panels', 'groups', 'client', row.id] } },
  ];

  protected isCardView: boolean;
  protected presetKeyValue = 'processList';

  private subscription: Subscription;

  ngOnInit(): void {
    this.isCardView = this.cardViewService.checkCardView(this.presetKeyValue);
    this.subscription = combineLatest([this.instances.activeNodeStates$, this.instances.productUpdates$]).subscribe(
      ([states, updates]) => {
        // actually not needed, we just use the same info source as server nodes to have info appear at the same time.
        if (!states) {
          this.nodeStateItems$.next([]);
          return;
        }

        const updAvail = updates?.newerVersionAvailable;
        const prodItem: StateItem = {
          name: this.node.nodeConfiguration.product.tag,
          type: updAvail ? 'update' : 'product',
          tooltip: `Product Version: ${this.node.nodeConfiguration.product.tag}${
            updAvail ? ' - Newer version available' : ''
          }`,
          click:
            updAvail && this.auth.isCurrentScopeWrite()
              ? () => {
                  this.areas.navigateBoth(
                    ['instances', 'configuration', this.areas.groupContext$.value, this.node.nodeConfiguration.id],
                    ['panels', 'instances', 'settings', 'product'],
                  );
                }
              : null,
        };

        this.nodeStateItems$.next([prodItem]);
      },
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }
}
