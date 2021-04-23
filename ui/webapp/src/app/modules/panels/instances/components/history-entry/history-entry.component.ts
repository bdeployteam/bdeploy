import { Component, OnDestroy, OnInit } from '@angular/core';
import { format } from 'date-fns';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { HistoryEntryDto, HistoryEntryType } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { HistoryService } from 'src/app/modules/primary/instances/services/history.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';

@Component({
  selector: 'app-history-entry',
  templateUrl: './history-entry.component.html',
  styleUrls: ['./history-entry.component.css'],
})
export class HistoryEntryComponent implements OnInit, OnDestroy {
  /* template */ entry$ = new BehaviorSubject<HistoryEntryDto>(null);
  /* template */ index$ = new BehaviorSubject<Number>(null);

  private subscription: Subscription;

  constructor(private areas: NavAreasService, private history: HistoryService, public instances: InstancesService) {
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
}
