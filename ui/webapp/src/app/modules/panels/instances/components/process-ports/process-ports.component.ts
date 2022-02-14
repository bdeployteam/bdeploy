import { Component, OnDestroy } from '@angular/core';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
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

      this.ports$.next(states.filter((s) => s.appUid === config.uid));
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
