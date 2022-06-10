import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, OnDestroy } from '@angular/core';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { CLIENT_NODE_NAME } from 'src/app/models/consts';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import {
  BdSearchable,
  SearchService,
} from 'src/app/modules/core/services/search.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { HistoryDetailsService } from '../../services/history-details.service';
import { ApplicationPair, ConfigPair, NodePair } from '../../utils/diff-utils';
import { InstanceConfigCache } from '../../utils/instance-utils';

@Component({
  selector: 'app-history-compare',
  templateUrl: './history-compare.component.html',
  styleUrls: ['./history-compare.component.css'],
})
export class HistoryCompareComponent implements OnDestroy, BdSearchable {
  /* template */ narrow$ = new BehaviorSubject<boolean>(false);

  /* template */ base$ = new BehaviorSubject<string>(null);
  /* template */ compare$ = new BehaviorSubject<string>(null);

  /* template */ configPair$ = new BehaviorSubject<ConfigPair>(null);
  /* template */ clientNodeName = CLIENT_NODE_NAME;

  /* template */ searchTerm = '';
  /* template */ showOnlyDifferences = true;

  private baseConfig$ = new BehaviorSubject<InstanceConfigCache>(null);
  private compareConfig$ = new BehaviorSubject<InstanceConfigCache>(null);
  private subscription: Subscription;

  constructor(
    private areas: NavAreasService,
    bop: BreakpointObserver,
    private details: HistoryDetailsService,
    public instances: InstancesService,
    private searchService: SearchService
  ) {
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
      combineLatest([this.baseConfig$, this.compareConfig$]).subscribe(
        ([base, compare]) => {
          if (!!base && !!compare) {
            const pair = new ConfigPair(base, compare);
            this.configPair$.next(pair);
          } else {
            this.configPair$.next(null);
          }
        }
      )
    );

    this.subscription.add(this.searchService.register(this));
  }

  /* template */ showAppPair(appPair: ApplicationPair): boolean {
    const showAll = !this.showOnlyDifferences;
    return showAll || appPair.hasDifferences;
  }

  /* template */ showNodePair(nodePair: NodePair): boolean {
    const hasApplications = !!nodePair?.applications?.length;
    const showAll = !this.showOnlyDifferences;
    return hasApplications && (showAll || nodePair.hasDifferences);
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  bdOnSearch(value: string) {
    this.searchTerm = value;
  }
}
