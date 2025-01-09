import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { BehaviorSubject, Subscription, combineLatest } from 'rxjs';
import { HistoryEntryDto } from 'src/app/models/gen.dtos';
import { BdDialogScrollEvent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { BdSearchable, SearchService } from 'src/app/modules/core/services/search.service';
import { histKey, histKeyEncode } from 'src/app/modules/panels/instances/utils/history-key.utils';
import { ServersService } from '../../../servers/services/servers.service';
import { HistoryColumnsService } from '../../services/history-columns.service';
import { HistoryService } from '../../services/history.service';
import { InstancesService } from '../../services/instances.service';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdServerSyncButtonComponent } from '../../../../core/components/bd-server-sync-button/bd-server-sync-button.component';
import { MatDivider } from '@angular/material/divider';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdDataTableComponent } from '../../../../core/components/bd-data-table/bd-data-table.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-history',
    templateUrl: './history.component.html',
    imports: [BdDialogComponent, BdDialogToolbarComponent, BdServerSyncButtonComponent, MatDivider, BdButtonComponent, BdDialogContentComponent, BdDataTableComponent, AsyncPipe]
})
export class HistoryComponent implements OnInit, BdSearchable, OnDestroy {
  private readonly cfg = inject(ConfigService);
  private readonly servers = inject(ServersService);
  private readonly search = inject(SearchService);
  protected readonly instances = inject(InstancesService);
  protected readonly columns = inject(HistoryColumnsService);
  protected readonly history = inject(HistoryService);

  protected showCreate$ = new BehaviorSubject<boolean>(true);
  protected showDeploy$ = new BehaviorSubject<boolean>(false);
  protected showRuntime$ = new BehaviorSubject<boolean>(false);
  protected runtimeLocked$ = new BehaviorSubject<boolean>(false);

  private subscription: Subscription;
  protected isCentral = false;

  protected getRecordRoute = (row: HistoryEntryDto) => {
    return [
      '',
      {
        outlets: {
          panel: ['panels', 'instances', 'history', histKeyEncode(histKey(row))],
        },
      },
    ];
  };

  ngOnInit(): void {
    this.subscription = combineLatest([this.showCreate$, this.showDeploy$, this.showRuntime$]).subscribe(
      ([create, deploy, runtime]) => {
        this.history.filter$.next({
          ...this.history.filter$.value,
          startTag: null,
          showCreateEvents: create,
          showDeploymentEvents: deploy,
          showRuntimeEvents: runtime,
        });
      },
    );

    // runtime events rely on live data from the server which is not allowed if not synchronized.
    this.subscription.add(
      combineLatest([this.servers.isCurrentInstanceSynchronized$, this.showRuntime$]).subscribe(([sync, show]) => {
        if (!sync && show) {
          this.showRuntime$.next(false);
        }
        this.runtimeLocked$.next(!sync);
      }),
    );

    this.subscription.add(this.search.register(this));
    this.subscription.add(
      this.cfg.isCentral$.subscribe((value) => {
        this.isCentral = value;
      }),
    );

    this.history.begin();
  }

  ngOnDestroy(): void {
    this.history.stop();
    this.subscription?.unsubscribe();
  }

  public bdOnSearch(search: string): void {
    this.history.filter$.next({
      ...this.history.filter$.value,
      filterText: search,
      startTag: null,
    });
  }

  protected onScrollContent(event: BdDialogScrollEvent) {
    if (event === BdDialogScrollEvent.NEAR_BOTTOM && !this.history.loading$.value) {
      this.history.more();
    }
  }
}
