import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { BehaviorSubject, Subscription, combineLatest } from 'rxjs';
import { PortsColumnsService } from 'src/app/modules/primary/instances/services/ports-columns.service';
import { NodeApplicationPort, PortsService } from 'src/app/modules/primary/instances/services/ports.service';
import { ProcessDetailsService } from '../../services/process-details.service';

@Component({
    selector: 'app-process-ports',
    templateUrl: './process-ports.component.html',
    standalone: false
})
export class ProcessPortsComponent implements OnInit, OnDestroy {
  private readonly ports = inject(PortsService);
  protected readonly details = inject(ProcessDetailsService);
  protected readonly columns = inject(PortsColumnsService);

  protected ports$ = new BehaviorSubject<NodeApplicationPort[]>(null);

  private subscription: Subscription;

  ngOnInit() {
    this.subscription = combineLatest([this.ports.activePortStates$, this.details.processConfig$]).subscribe(
      ([states, config]) => {
        if (!states || !config) {
          this.ports$.next(null);
          return;
        }

        this.ports$.next(states.filter((s) => s.appId === config.id));
      },
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }
}
