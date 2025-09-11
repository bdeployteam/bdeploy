import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { InstanceNodeConfigurationListDto, NodeType } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { BdSearchable, SearchService } from 'src/app/modules/core/services/search.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { HistoryDetailsService } from '../../services/history-details.service';
import { InstanceConfigCache } from '../../utils/instance-utils';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { HistoryHeaderConfigComponent } from '../history-header-config/history-header-config.component';
import { HistoryVariablesConfigComponent } from '../history-variables-config/history-variables-config.component';
import { HistoryProcessConfigComponent } from '../history-process-config/history-process-config.component';
import { AsyncPipe } from '@angular/common';
import { NodeFilterPipe } from '../../utils/filter-node';

@Component({
    selector: 'app-history-view',
    templateUrl: './history-view.component.html',
    styleUrls: ['./history-view.component.css'],
    imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, HistoryHeaderConfigComponent, HistoryVariablesConfigComponent, HistoryProcessConfigComponent, AsyncPipe, NodeFilterPipe]
})
export class HistoryViewComponent implements OnInit, OnDestroy, BdSearchable {
  private readonly areas = inject(NavAreasService);
  private readonly details = inject(HistoryDetailsService);
  private readonly searchService = inject(SearchService);
  protected readonly instances = inject(InstancesService);

  protected base$ = new BehaviorSubject<string>(null);
  protected config$ = new BehaviorSubject<InstanceConfigCache>(null);
  protected clientNodeType = NodeType.CLIENT;
  protected searchTerm = '';

  private subscription: Subscription;

  ngOnInit() {
    this.subscription = this.areas.panelRoute$.subscribe((route) => {
      const base = route?.paramMap?.get('base');
      if (!base) {
        this.base$.next(null);
      } else {
        this.base$.next(base);
        this.details.getVersionDetails(base).subscribe((config) => {
          this.config$.next(config);
        });
      }
    });

    this.subscription.add(this.searchService.register(this));
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  public bdOnSearch(value: string) {
    this.searchTerm = value;
  }

  protected getAppDesc(nodes: InstanceNodeConfigurationListDto, name: string) {
    return nodes?.applications.find((a) => a.key.name === name)?.descriptor;
  }
}
