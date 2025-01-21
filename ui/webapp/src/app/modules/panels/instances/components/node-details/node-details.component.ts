import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject, Subscription, combineLatest } from 'rxjs';
import { InstanceNodeConfigurationDto, MinionStatusDto } from 'src/app/models/gen.dtos';
import { convert2String } from 'src/app/modules/core/utils/version.utils';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdNotificationCardComponent } from '../../../../core/components/bd-notification-card/bd-notification-card.component';
import { NodeHeaderComponent } from '../../../../primary/instances/components/dashboard/server-node/header/header.component';
import { AsyncPipe, DatePipe } from '@angular/common';

@Component({
    selector: 'app-node-details',
    templateUrl: './node-details.component.html',
    imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, BdNotificationCardComponent, NodeHeaderComponent, AsyncPipe, DatePipe]
})
export class NodeDetailsComponent implements OnInit, OnDestroy {
  private readonly instances = inject(InstancesService);
  private readonly route = inject(ActivatedRoute);

  protected nodeName$ = new BehaviorSubject<string>(null);
  protected nodeState$ = new BehaviorSubject<MinionStatusDto>(null);
  protected nodeCfg$ = new BehaviorSubject<InstanceNodeConfigurationDto>(null);
  protected nodeVersion: string;
  private subscription: Subscription;

  ngOnInit(): void {
    this.subscription = this.route.paramMap.subscribe((params) => {
      const node = params.get('node');
      this.nodeName$.next(node);
    });

    this.subscription.add(
      combineLatest([this.nodeName$, this.instances.activeNodeStates$, this.instances.activeNodeCfgs$]).subscribe(
        ([node, states, cfgs]) => {
          if (!node || !states) {
            this.nodeState$.next(null);
            this.nodeCfg$.next(null);
          } else {
            this.nodeState$.next(states[node]);

            if (cfgs?.nodeConfigDtos?.length) {
              this.nodeCfg$.next(cfgs.nodeConfigDtos.find((d) => d.nodeName === node));
            }
            this.nodeVersion = convert2String(this.nodeState$.value?.config.version);
          }
        },
      ),
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }
}
