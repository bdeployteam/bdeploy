import { Component, Input, OnInit, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { ApplicationConfiguration } from 'src/app/models/gen.dtos';
import { InstancesService } from '../../../services/instances.service';
import { ProcessesService } from '../../../services/processes.service';

@Component({
    selector: 'app-process-outdated',
    templateUrl: './process-outdated.component.html',
    styleUrls: ['./process-outdated.component.css'],
    standalone: false
})
export class ProcessOutdatedComponent implements OnInit {
  private readonly processes = inject(ProcessesService);
  private readonly instances = inject(InstancesService);

  @Input() record: ApplicationConfiguration;

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
