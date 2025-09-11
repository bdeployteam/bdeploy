import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { BehaviorSubject, Subscription, combineLatest } from 'rxjs';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { BdSearchable, SearchService } from 'src/app/modules/core/services/search.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { HistoryDetailsService } from '../../services/history-details.service';
import { ApplicationPair, ConfigPair, NodePair } from '../../utils/diff-utils';
import { InstanceConfigCache } from '../../utils/instance-utils';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { HistoryHeaderConfigComponent } from '../history-header-config/history-header-config.component';
import { HistoryVariablesConfigComponent } from '../history-variables-config/history-variables-config.component';
import { HistoryProcessConfigComponent } from '../history-process-config/history-process-config.component';
import { AsyncPipe } from '@angular/common';
import { CustomNodeFilterPipe } from '../../utils/filter-node';
import { NodeType } from 'src/app/models/gen.dtos';

@Component({
    selector: 'app-history-compare',
    templateUrl: './history-compare.component.html',
    styleUrls: ['./history-compare.component.css'],
    imports: [BdDialogComponent, BdDialogToolbarComponent, BdButtonComponent, BdDialogContentComponent, HistoryHeaderConfigComponent, HistoryVariablesConfigComponent, HistoryProcessConfigComponent, AsyncPipe, CustomNodeFilterPipe]
})
export class HistoryCompareComponent implements OnInit, OnDestroy, BdSearchable {
  private readonly areas = inject(NavAreasService);
  private readonly bop = inject(BreakpointObserver);
  private readonly details = inject(HistoryDetailsService);
  private readonly searchService = inject(SearchService);
  protected readonly instances = inject(InstancesService);

  protected narrow$ = new BehaviorSubject<boolean>(false);

  protected base$ = new BehaviorSubject<string>(null);
  protected compare$ = new BehaviorSubject<string>(null);

  protected configPair$ = new BehaviorSubject<ConfigPair>(null);
  protected clientNodeType = NodeType.CLIENT;

  protected searchTerm = '';
  protected showOnlyDifferences = true;

  private readonly baseConfig$ = new BehaviorSubject<InstanceConfigCache>(null);
  private readonly compareConfig$ = new BehaviorSubject<InstanceConfigCache>(null);
  private subscription: Subscription;

  ngOnInit() {
    this.subscription = this.bop.observe('(max-width: 800px)').subscribe((bs) => {
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
      }),
    );

    this.subscription.add(
      combineLatest([this.baseConfig$, this.compareConfig$]).subscribe(([base, compare]) => {
        if (!!base && !!compare) {
          const pair = new ConfigPair(base, compare);
          this.configPair$.next(pair);
        } else {
          this.configPair$.next(null);
        }
      }),
    );

    this.subscription.add(this.searchService.register(this));
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected showAppPair(appPair: ApplicationPair): boolean {
    const showAll = !this.showOnlyDifferences;
    return showAll || appPair.hasDifferences;
  }

  protected showNodePair(nodePair: NodePair): boolean {
    const hasApplications = !!nodePair?.applications?.length;
    const showAll = !this.showOnlyDifferences;
    return hasApplications && (showAll || nodePair.hasDifferences);
  }

  public bdOnSearch(value: string) {
    this.searchTerm = value;
  }
}
