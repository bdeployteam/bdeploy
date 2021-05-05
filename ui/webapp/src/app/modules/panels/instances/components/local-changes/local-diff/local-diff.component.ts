import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, OnInit } from '@angular/core';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { CLIENT_NODE_NAME } from 'src/app/models/consts';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ConfigPair, NodePair } from '../../../utils/diff-utils';

@Component({
  selector: 'app-local-diff',
  templateUrl: './local-diff.component.html',
  styleUrls: ['./local-diff.component.css'],
})
export class LocalDiffComponent implements OnInit {
  /* template */ narrow$ = new BehaviorSubject<boolean>(false);
  /* template */ configPair$ = new BehaviorSubject<ConfigPair>(null);

  private subscription: Subscription;

  constructor(public edit: InstanceEditService, bop: BreakpointObserver) {
    this.subscription = bop.observe('(max-width: 800px)').subscribe((bs) => {
      this.narrow$.next(bs.matches);
    });
  }

  ngOnInit(): void {
    this.subscription.add(
      combineLatest([this.edit.current$, this.edit.base$, this.edit.state$, this.edit.baseApplications$, this.edit.stateApplications$]).subscribe(
        ([instance, base, compare, baseApps, compareApps]) => {
          if (!instance || !base || !compare) {
            return;
          }
          const baseCache = {
            config: base.config,
            nodes: { applications: baseApps, nodeConfigDtos: base.nodeDtos },
            version: `Server (${instance.instance.tag})`,
          };
          const localCache = { config: compare.config, nodes: { applications: compareApps, nodeConfigDtos: compare.nodeDtos }, version: 'Local Changes' };

          this.configPair$.next(new ConfigPair(baseCache, localCache));
        }
      )
    );
  }

  /* template */ getNodeName(node: NodePair) {
    return node.name === CLIENT_NODE_NAME ? 'Client Applications' : node.name;
  }

  /* template */ hasProcessControl(node: NodePair) {
    return node.name !== CLIENT_NODE_NAME;
  }
}
