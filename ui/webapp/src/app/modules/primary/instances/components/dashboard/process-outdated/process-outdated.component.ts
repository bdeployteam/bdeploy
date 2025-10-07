import { Component, inject, Input, OnInit, signal } from '@angular/core';
import { InstancesService } from '../../../services/instances.service';
import { ProcessesService } from '../../../services/processes.service';
import { MatTooltip } from '@angular/material/tooltip';
import { BdDataColumn } from '../../../../../../models/data';
import { CellComponent } from '../../../../../core/components/bd-data-component-cell/bd-data-component-cell.component';
import { ProcessDisplayData } from '../../../services/processes-columns.service';

@Component({
  selector: 'app-process-outdated',
  templateUrl: './process-outdated.component.html',
  styleUrls: ['./process-outdated.component.css'],
  imports: [MatTooltip]
})
export class ProcessOutdatedComponent implements OnInit, CellComponent<ProcessDisplayData, string> {
  private readonly processes = inject(ProcessesService);
  private readonly instances = inject(InstancesService);

  @Input() record: ProcessDisplayData;
  @Input() column: BdDataColumn<ProcessDisplayData, string>;

  protected isRunning = signal(false);
  protected isOutdated = signal(false);
  protected tooltip = signal('');

  ngOnInit(): void {
    this.processes.processStates$.subscribe((s) => {
      const states = ProcessesService.getAppStates(s, this.record.id);
      if (!states) {
        // no information about the process
        this.updateSignalsForSingleNode(false, false);
        return;
      }

      if (this.record.serverNode) {
        const status = states?.[this.record.serverNode];
        const isRunning = ProcessesService.isRunning(status?.processState);
        this.updateSignalsForSingleNode(isRunning, isRunning && status?.instanceTag !== this.instances.active$.value?.instance?.tag);
      } else {
        const totalProcesses = Object.entries(states).length;
        let nrOfOutdatedApps = 0;
        let nrOfRunningApps = 0;
        Object.entries(states).forEach(entry => {
          const status = entry[1];
          if (ProcessesService.isRunning(status.processState)) {
            nrOfRunningApps++;
            nrOfOutdatedApps += status.instanceTag !== this.instances.active$.value?.instance?.tag ? 1 : 0;
          }
        });

        this.updateSignalsForMultiNode(totalProcesses, nrOfRunningApps, nrOfOutdatedApps);
      }
    });
  }

  private updateSignalsForSingleNode(isRunning: boolean, isOutdated: boolean) {
    this.isOutdated.set(isOutdated);
    this.isRunning.set(isRunning);

    if (isOutdated) {
      this.tooltip.set('The process is still running in a non-active version.');
    } else {
      if (isRunning) {
        this.tooltip.set('The process is running in the current version.');
      } else {
        this.tooltip.set('');
      }
    }
  }

  private updateSignalsForMultiNode(total: number, nrOfRunningApps: number, nrOfOutdatedApps: number) {
    const hasAtLeastOneRunningApp = nrOfOutdatedApps > 0;
    const hasAtLeastOneOutdatedApp = nrOfOutdatedApps > 0;

    this.isOutdated.set(hasAtLeastOneOutdatedApp);
    this.isRunning.set(hasAtLeastOneRunningApp);

    if (hasAtLeastOneOutdatedApp) {
      this.tooltip.set(`${nrOfOutdatedApps}/${total}`);
    } else {
      if (hasAtLeastOneRunningApp) {
        this.tooltip.set('All running apps are up to date.');
      } else {
        this.tooltip.set('');
      }
    }
  }
}
