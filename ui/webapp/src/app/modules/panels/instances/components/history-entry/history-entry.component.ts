import { Component, OnDestroy, OnInit } from '@angular/core';
import { format } from 'date-fns';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { HistoryEntryDto, HistoryEntryType, InstanceStateRecord } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { HistoryService } from 'src/app/modules/primary/instances/services/history.service';
import { InstanceStateService } from 'src/app/modules/primary/instances/services/instance-state.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';

@Component({
  selector: 'app-history-entry',
  templateUrl: './history-entry.component.html',
  styleUrls: ['./history-entry.component.css'],
})
export class HistoryEntryComponent implements OnInit, OnDestroy {
  /* template */ entry$ = new BehaviorSubject<HistoryEntryDto>(null);
  /* template */ index$ = new BehaviorSubject<Number>(null);
  /* template */ state$ = new BehaviorSubject<InstanceStateRecord>(null);

  /* template */ installing$ = new BehaviorSubject<boolean>(false);
  /* template */ uninstalling$ = new BehaviorSubject<boolean>(false);
  /* template */ activating$ = new BehaviorSubject<boolean>(false);

  private subscription: Subscription;

  constructor(
    private areas: NavAreasService,
    private history: HistoryService,
    public instances: InstancesService,
    public states: InstanceStateService,
    public servers: ServersService
  ) {
    this.subscription = combineLatest([this.areas.panelRoute$, this.history.history$]).subscribe(([route, entries]) => {
      // Note: basing the selection on an index in the service has some drawbacks, but we can do that now without needing to change a lot in the backend.
      const idx = route?.paramMap?.get('index');
      if (!idx || !entries || Number(idx) >= entries.length) {
        this.entry$.next(null);
        this.index$.next(null);
      } else {
        this.entry$.next(entries[Number(idx)]);
        this.index$.next(Number(idx));
      }
    });

    this.subscription.add(this.states.state$.subscribe((s) => this.state$.next(s)));
  }

  ngOnInit(): void {}

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ formatTimestamp(x: number) {
    return format(x, 'dd.MM.yyy HH:mm:ss');
  }

  /* template */ isCreate(entry: HistoryEntryDto) {
    return entry.type === HistoryEntryType.CREATE;
  }

  /* template */ isInstalled() {
    return !!this.state$.value?.installedTags?.find((s) => s === this.entry$.value?.instanceTag);
  }

  /* template */ isActive() {
    return this.state$.value?.activeTag === this.entry$.value?.instanceTag;
  }

  /* template */ doInstall() {
    this.installing$.next(true);
    this.states
      .install(this.entry$.value.instanceTag)
      .pipe(finalize(() => this.installing$.next(false)))
      .subscribe();
  }
  /* template */ doUninstall() {
    this.uninstalling$.next(true);
    this.states
      .uninstall(this.entry$.value.instanceTag)
      .pipe(finalize(() => this.uninstalling$.next(false)))
      .subscribe();
  }
  /* template */ doActivate() {
    this.activating$.next(true);
    this.states
      .activate(this.entry$.value.instanceTag)
      .pipe(finalize(() => this.activating$.next(false)))
      .subscribe();
  }
}
