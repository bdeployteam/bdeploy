import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { CLIENT_NODE_NAME } from 'src/app/models/consts';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { HistoryDetailsService } from '../../services/history-details.service';
import { ConfigPair, NodePair } from '../../utils/diff-utils';
import { InstanceConfigCache } from '../../utils/instance-utils';

@Component({
  selector: 'app-history-compare',
  templateUrl: './history-compare.component.html',
  styleUrls: ['./history-compare.component.css'],
})
export class HistoryCompareComponent implements OnInit, OnDestroy {
  /* template */ narrow$ = new BehaviorSubject<boolean>(false);

  /* template */ base$ = new BehaviorSubject<string>(null);
  /* template */ compare$ = new BehaviorSubject<string>(null);

  /* template */ configPair$ = new BehaviorSubject<ConfigPair>(null);

  private baseConfig$ = new BehaviorSubject<InstanceConfigCache>(null);
  private compareConfig$ = new BehaviorSubject<InstanceConfigCache>(null);
  private subscription: Subscription;

  constructor(private areas: NavAreasService, bop: BreakpointObserver, private details: HistoryDetailsService, public instances: InstancesService) {
    this.subscription = bop.observe('(max-width: 800px)').subscribe((bs) => {
      this.narrow$.next(bs.matches);
    });

    this.subscription.add(
      this.areas.panelRoute$.subscribe((route) => {
        let base = route?.paramMap?.get('base');
        let compare = route?.paramMap?.get('compare');

        if (!base || !compare) {
          this.base$.next(null);
        } else {
          if (Number(base) > Number(compare)) {
            // swap to always have newer version RIGHT.
            [base, compare] = [compare, base];
          }

          this.base$.next(base);
          this.details.getVersionDetails(base).subscribe((config) => {
            this.baseConfig$.next(config);
          });

          this.compare$.next(compare);
          this.details.getVersionDetails(compare).subscribe((config) => {
            this.compareConfig$.next(config);
          });
        }
      })
    );

    this.subscription.add(
      combineLatest([this.baseConfig$, this.compareConfig$]).subscribe(([base, compare]) => {
        if (!!base && !!compare) {
          const pair = new ConfigPair(base, compare);
          this.configPair$.next(pair);
        } else {
          this.configPair$.next(null);
        }
      })
    );
  }

  ngOnInit(): void {}

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ getNodeName(node: NodePair) {
    return node.name === CLIENT_NODE_NAME ? 'Client Applications' : node.name;
  }

  /* template */ hasProcessControl(node: NodePair) {
    return node.name !== CLIENT_NODE_NAME;
  }
}
