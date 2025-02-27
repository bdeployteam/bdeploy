import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject, Subscription, combineLatest } from 'rxjs';
import { InstanceNodeConfigurationDto, MinionStatusDto } from 'src/app/models/gen.dtos';
import { convert2String } from 'src/app/modules/core/utils/version.utils';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';

@Component({
    selector: 'app-node-details',
    templateUrl: './node-details.component.html',
    standalone: false
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
