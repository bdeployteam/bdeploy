import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { InstanceNodeConfigurationDto, MinionStatusDto } from 'src/app/models/gen.dtos';
import { convert2String } from 'src/app/modules/core/utils/version.utils';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';

@Component({
  selector: 'app-node-details',
  templateUrl: './node-details.component.html',
  styleUrls: ['./node-details.component.css'],
})
export class NodeDetailsComponent implements OnInit, OnDestroy {
  /* template */ nodeName$ = new BehaviorSubject<string>(null);
  /* template */ nodeState$ = new BehaviorSubject<MinionStatusDto>(null);
  /* template */ nodeCfg$ = new BehaviorSubject<InstanceNodeConfigurationDto>(null);
  /* template */ nodeVersion: string;
  private subscription: Subscription;

  constructor(private instances: InstancesService, route: ActivatedRoute) {
    this.subscription = route.paramMap.subscribe((params) => {
      const node = params.get('node');
      this.nodeName$.next(node);
    });
  }

  ngOnInit(): void {
    this.subscription.add(
      combineLatest([this.nodeName$, this.instances.activeNodeStates$, this.instances.activeNodeCfgs$]).subscribe(([node, states, cfgs]) => {
        if (!node || !states) {
          this.nodeState$.next(null);
        } else {
          this.nodeState$.next(states[node]);

          if (!!cfgs?.nodeConfigDtos?.length) {
            this.nodeCfg$.next(cfgs.nodeConfigDtos.find((d) => d.nodeName === node));
          }
          this.nodeVersion = convert2String(this.nodeState$.value?.config.version);
        }
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
