import {
  Component,
  HostBinding,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  SimpleChanges,
} from '@angular/core';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import {
  ApplicationConfiguration,
  ProcessState,
  ProcessStatusDto,
} from 'src/app/models/gen.dtos';
import { ProcessesService } from '../../../services/processes.service';

@Component({
  selector: 'app-process-status-icon',
  templateUrl: './process-status-icon.component.html',
  styleUrls: ['./process-status-icon.component.css'],
})
export class ProcessStatusIconComponent
  implements OnInit, OnChanges, OnDestroy
{
  @Input() record: ApplicationConfiguration;

  @HostBinding('attr.data-cy') dataCy: string;

  /* template */ icon$ = new BehaviorSubject<string>('help');
  /* template */ svgIcon$ = new BehaviorSubject<string>(null);
  /* template */ hint$ = new BehaviorSubject<string>('Unknown');
  /* template */ class$ = new BehaviorSubject<string>('local-unknown');

  private change$ = new BehaviorSubject<any>(null);
  private subscription: Subscription;

  constructor(private processes: ProcessesService) {}

  ngOnInit(): void {
    this.subscription = combineLatest([
      this.processes.processStates$,
      this.change$,
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
    ]).subscribe(([ps, _]) => {
      if (this.record) {
        this.update(ps);
      }
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    this.change$.next(changes);
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private update(ps: { [key: string]: ProcessStatusDto }) {
    const state = ProcessesService.get(ps, this.record.id);
    if (!state) {
      this.next('help', null, 'Unknown', 'local-unknown');
      return;
    }

    this.dataCy = state.processState;

    switch (state.processState) {
      case ProcessState.STOPPED:
        return this.next('stop', null, 'Stopped', 'local-stopped');
      case ProcessState.STOPPED_START_PLANNED:
        return this.next(
          null,
          'start-scheduled',
          'Process scheduled to start',
          'local-stopped'
        );
      case ProcessState.RUNNING_NOT_STARTED:
        return this.next(
          null,
          'start-scheduled',
          'Process starting',
          'local-running'
        );
      case ProcessState.RUNNING:
        return this.next('favorite', null, 'Running', 'local-running');
      case ProcessState.RUNNING_UNSTABLE:
        return this.next(
          'favorite',
          null,
          'Running (Recently Crashed)',
          'local-crashed'
        );
      case ProcessState.RUNNING_NOT_ALIVE:
        return this.next(
          'heart_broken',
          null,
          'Process lifeness probe reported a problem in the running process',
          'local-crashed'
        );
      case ProcessState.RUNNING_STOP_PLANNED:
        return this.next(
          null,
          'stop-scheduled',
          'Running (Stop Planned)',
          'local-running'
        );
      case ProcessState.CRASHED_WAITING:
        return this.next(
          'report_problem',
          null,
          'Crashed (Restart pending)',
          'local-crashed'
        );
      case ProcessState.CRASHED_PERMANENTLY:
        return this.next(
          'error',
          null,
          'Crashed (Too many retries, stopped)',
          'local-crashed'
        );
    }
  }

  private next(icon: string, svgIcon: string, hint: string, cls: string) {
    this.icon$.next(icon);
    this.svgIcon$.next(svgIcon);
    this.hint$.next(hint);
    this.class$.next(cls);
  }
}
