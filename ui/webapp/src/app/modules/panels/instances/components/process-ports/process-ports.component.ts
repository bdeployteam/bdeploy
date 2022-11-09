import { Component, OnDestroy, ViewChild } from '@angular/core';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { BdDataTableComponent } from 'src/app/modules/core/components/bd-data-table/bd-data-table.component';
import { PortsColumnsService } from 'src/app/modules/primary/instances/services/ports-columns.service';
import {
  NodeApplicationPort,
  PortsService,
} from 'src/app/modules/primary/instances/services/ports.service';
import { ProcessDetailsService } from '../../services/process-details.service';

@Component({
  selector: 'app-process-ports',
  templateUrl: './process-ports.component.html',
})
export class ProcessPortsComponent implements OnDestroy {
  /* template */ ports$ = new BehaviorSubject<NodeApplicationPort[]>(null);

  @ViewChild(BdDataTableComponent)
  table: BdDataTableComponent<NodeApplicationPort>;

  private subscription: Subscription;

  constructor(
    ports: PortsService,
    public details: ProcessDetailsService,
    public columns: PortsColumnsService
  ) {
    this.subscription = combineLatest([
      ports.activePortStates$,
      details.processConfig$,
    ]).subscribe(([states, config]) => {
      if (!states || !config) {
        this.ports$.next(null);
        return;
      }

      this.ports$.next(states.filter((s) => s.appId === config.id));
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
