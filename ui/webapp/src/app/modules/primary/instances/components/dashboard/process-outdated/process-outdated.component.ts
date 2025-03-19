import { Component, Input, OnInit, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { ApplicationConfiguration } from 'src/app/models/gen.dtos';
import { InstancesService } from '../../../services/instances.service';
import { ProcessesService } from '../../../services/processes.service';
import { MatTooltip } from '@angular/material/tooltip';
import { AsyncPipe } from '@angular/common';
import { BdDataColumn } from '../../../../../../models/data';
import { CellComponent } from '../../../../../core/components/bd-data-component-cell/bd-data-component-cell.component';

@Component({
    selector: 'app-process-outdated',
    templateUrl: './process-outdated.component.html',
    styleUrls: ['./process-outdated.component.css'],
    imports: [MatTooltip, AsyncPipe]
})
export class ProcessOutdatedComponent implements OnInit, CellComponent<ApplicationConfiguration, string> {
  private readonly processes = inject(ProcessesService);
  private readonly instances = inject(InstancesService);

  @Input() record: ApplicationConfiguration;
  @Input() column: BdDataColumn<ApplicationConfiguration, string>;

  protected running$ = new BehaviorSubject<boolean>(false);
  protected outdated$ = new BehaviorSubject<boolean>(false);

  ngOnInit(): void {
    this.processes.processStates$.subscribe((s) => {
      const status = ProcessesService.get(s, this.record.id);
      if (!status) {
        // no information about the process
        this.outdated$.next(false);
        this.running$.next(false);
      } else {
        const isRunning = ProcessesService.isRunning(status.processState);
        this.running$.next(isRunning);
        this.outdated$.next(isRunning && status.instanceTag !== this.instances.active$.value?.instance?.tag);
      }
    });
  }
}
