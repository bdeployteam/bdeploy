import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { BehaviorSubject, Subscription, combineLatest } from 'rxjs';
import { PortsColumnsService } from 'src/app/modules/primary/instances/services/ports-columns.service';
import { NodeApplicationPort, PortsService } from 'src/app/modules/primary/instances/services/ports.service';
import { ProcessDetailsService } from '../../services/process-details.service';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdDataDisplayComponent } from '../../../../core/components/bd-data-display/bd-data-display.component';
import { BdNoDataComponent } from '../../../../core/components/bd-no-data/bd-no-data.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-process-ports',
    templateUrl: './process-ports.component.html',
    imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, BdDataDisplayComponent, BdNoDataComponent, AsyncPipe]
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
