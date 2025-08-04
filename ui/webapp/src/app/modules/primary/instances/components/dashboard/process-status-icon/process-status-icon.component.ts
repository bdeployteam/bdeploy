import { Component, HostBinding, inject, Input, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import { BehaviorSubject, combineLatest, of, Subscription } from 'rxjs';
import { Actions, ApplicationConfiguration, ProcessState, ProcessStatusDto } from 'src/app/models/gen.dtos';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { ProcessesService } from '../../../services/processes.service';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { AsyncPipe, NgClass } from '@angular/common';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { BdDataColumn } from '../../../../../../models/data';
import { CellComponent } from '../../../../../core/components/bd-data-component-cell/bd-data-component-cell.component';

@Component({
    selector: 'app-process-status-icon',
    templateUrl: './process-status-icon.component.html',
    styleUrls: ['./process-status-icon.component.css'],
    imports: [
        MatIcon,
        MatTooltip,
        NgClass,
        MatProgressSpinner,
        AsyncPipe,
    ],
})
export class ProcessStatusIconComponent implements OnInit, OnChanges, OnDestroy, CellComponent<ApplicationConfiguration, string> {
  private readonly processes = inject(ProcessesService);
  private readonly actions = inject(ActionsService);

  @Input() record: ApplicationConfiguration;
  @Input() column: BdDataColumn<ApplicationConfiguration, string>;
  @HostBinding('attr.data-testid') dataCy: string;

  protected icon$ = new BehaviorSubject<string>('help');
  protected svgIcon$ = new BehaviorSubject<string>(null);
  protected hint$ = new BehaviorSubject<string>('Unknown');
  protected class$ = new BehaviorSubject<string>('local-unknown');

  private readonly id$ = new BehaviorSubject<string>(null);
  private readonly change$ = new BehaviorSubject<unknown>(null);
  private subscription: Subscription;

  protected mappedAction$ = this.actions.action(
    [Actions.START_PROCESS, Actions.STOP_PROCESS],
    of(false),
    null,
    null,
    this.id$
  );

  ngOnInit(): void {
    this.subscription = combineLatest([
      this.processes.processStates$,
      this.change$,
    ]).subscribe(([ps, _]) => {
      if (this.record) {
        this.id$.next(this.record.id);
        this.update(ps);
      }
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    this.change$.next(changes);
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  private update(ps: Record<string, ProcessStatusDto>) {
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
        return this.next(null, 'start-scheduled', 'Process scheduled to start', 'local-stopped');
      case ProcessState.RUNNING_NOT_STARTED:
        return this.next(null, 'start-scheduled', 'Process starting', 'local-running');
      case ProcessState.RUNNING:
        return this.next('favorite', null, 'Running', 'local-running');
      case ProcessState.RUNNING_UNSTABLE:
        return this.next('favorite', null, 'Running (Recently Crashed)', 'local-crashed');
      case ProcessState.RUNNING_NOT_ALIVE:
        return this.next(
          'heart_broken',
          null,
          'Process liveness probe reported a problem in the running process',
          'local-crashed'
        );
      case ProcessState.RUNNING_STOP_PLANNED:
        return this.next(null, 'stop-scheduled', 'Running (Stop Planned)', 'local-running');
      case ProcessState.CRASHED_WAITING:
        return this.next('report_problem', null, 'Crashed (Restart pending)', 'local-crashed');
      case ProcessState.CRASHED_PERMANENTLY:
        return this.next('error', null, 'Crashed (Too many retries, stopped)', 'local-crashed');
    }
  }

  private next(icon: string, svgIcon: string, hint: string, cls: string) {
    this.icon$.next(icon);
    this.svgIcon$.next(svgIcon);
    this.hint$.next(hint);
    this.class$.next(cls);
  }
}
