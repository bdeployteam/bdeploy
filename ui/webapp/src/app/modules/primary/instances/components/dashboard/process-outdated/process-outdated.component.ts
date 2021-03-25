import { Component, Input, OnInit } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { ApplicationConfiguration } from 'src/app/models/gen.dtos';
import { InstancesService } from '../../../services/instances.service';
import { ProcessesService } from '../../../services/processes.service';

@Component({
  selector: 'app-process-outdated',
  templateUrl: './process-outdated.component.html',
  styleUrls: ['./process-outdated.component.css'],
})
export class ProcessOutdatedComponent implements OnInit {
  @Input() record: ApplicationConfiguration;

  /* template */ running$ = new BehaviorSubject<boolean>(false);
  /* template */ outdated$ = new BehaviorSubject<boolean>(false);

  constructor(private processes: ProcessesService, private instances: InstancesService) {}

  ngOnInit(): void {
    this.processes.processStates$.subscribe((s) => {
      const status = ProcessesService.get(s, this.record.uid);
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
