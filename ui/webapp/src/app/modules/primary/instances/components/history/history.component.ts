import { Component, OnDestroy, OnInit } from '@angular/core';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { HistoryEntryDto } from 'src/app/models/gen.dtos';
import { BdDialogScrollEvent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import {
  BdSearchable,
  SearchService,
} from 'src/app/modules/core/services/search.service';
import {
  histKey,
  histKeyEncode,
} from 'src/app/modules/panels/instances/utils/history-key.utils';
import { ServersService } from '../../../servers/services/servers.service';
import { HistoryColumnsService } from '../../services/history-columns.service';
import { HistoryService } from '../../services/history.service';
import { InstancesService } from '../../services/instances.service';

@Component({
  selector: 'app-history',
  templateUrl: './history.component.html',
})
export class HistoryComponent implements OnInit, BdSearchable, OnDestroy {
  /* template */ showCreate$ = new BehaviorSubject<boolean>(true);
  /* template */ showDeploy$ = new BehaviorSubject<boolean>(false);
  /* template */ showRuntime$ = new BehaviorSubject<boolean>(false);

  private subscription: Subscription;
  /* template */ public isCentral = false;

  /* template */ getRecordRoute = (row: HistoryEntryDto) => {
    return [
      '',
      {
        outlets: {
          panel: [
            'panels',
            'instances',
            'history',
            histKeyEncode(histKey(row)),
          ],
        },
      },
    ];
  };

  constructor(
    public cfg: ConfigService,
    public instances: InstancesService,
    public servers: ServersService,
    public columns: HistoryColumnsService,
    public history: HistoryService,
    private search: SearchService
  ) {
    this.subscription = combineLatest([
      this.showCreate$,
      this.showDeploy$,
      this.showRuntime$,
    ]).subscribe(([create, deploy, runtime]) => {
      this.history.filter$.next({
        ...this.history.filter$.value,
        startTag: null,
        showCreateEvents: create,
        showDeploymentEvents: deploy,
        showRuntimeEvents: runtime,
      });
    });

    this.subscription.add(this.search.register(this));
    this.subscription.add(
      this.cfg.isCentral$.subscribe((value) => {
        this.isCentral = value;
      })
    );
  }

  ngOnInit(): void {
    this.history.begin();
  }

  ngOnDestroy(): void {
    this.history.stop();
    this.subscription.unsubscribe();
  }

  bdOnSearch(search: string): void {
    this.history.filter$.next({
      ...this.history.filter$.value,
      filterText: search,
    });
  }

  /* template */ onScrollContent(event: BdDialogScrollEvent) {
    if (
      event === BdDialogScrollEvent.NEAR_BOTTOM &&
      !this.history.loading$.value
    ) {
      this.history.more();
    }
  }
}
