import { Component, inject, Input, OnInit, signal } from '@angular/core';
import { BdDataColumn } from 'src/app/models/data';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { CellComponent } from '../../../../core/components/bd-data-component-cell/bd-data-component-cell.component';
import { ProcessesService } from '../../services/processes.service';
import { PortsService } from '../../services/ports.service';
import { NgClass } from '@angular/common';
import { ProcessDisplayData } from '../../services/processes-columns.service';

@Component({
  selector: 'app-port-status-column',
  templateUrl: './port-status-column.component.html',
  styleUrls: ['./port-status-column.component.css'],
  imports: [MatIcon, MatTooltip, NgClass]
})
export class PortStatusColumnComponent implements CellComponent<ProcessDisplayData, boolean>, OnInit {
  private readonly ports = inject(PortsService);
  private readonly processes = inject(ProcessesService);

  @Input() record: ProcessDisplayData;
  @Input() column: BdDataColumn<ProcessDisplayData, boolean>;

  protected statusStyleClass = signal('local-unknown');
  protected tooltip = signal('');
  protected icon = signal('help');

  ngOnInit(): void {
    this.ports.activePortStates$.subscribe((activePortStates) => {
      if (!activePortStates || !this.record || !activePortStates?.[this.record.id]?.portStates) {
        this.icon.set('help');
        this.statusStyleClass.set('local-unknown');
        this.tooltip.set('');
      }

      const appPorts = activePortStates?.[this.record.id]?.portStates;
      const processesWithBrokenPorts = new Set<string>();
      appPorts?.forEach(compositeState =>
        compositeState.states
          .filter(portState => !PortsService.isOk(portState))
          .forEach((portInBrokenState) => {
            processesWithBrokenPorts.add(portInBrokenState.serverNode);
          })
      );


      this.icon.set((processesWithBrokenPorts.size > 0) ? 'bd-warning-text' : 'bd-success-text');
      if (processesWithBrokenPorts.size == 0) {
        this.tooltip.set(`All ports are in their required state.`);
        this.icon.set('done');
      } else {
        this.icon.set('warning');
        if (this.record.serverNode) {
          this.tooltip.set(`One or more ports are not in the required state.`);
        } else {
          const totalProcesses = ProcessesService.getNumberOfProcesses(this.processes.processStates$.value, this.record.id);
          this.tooltip.set(`${processesWithBrokenPorts.size}/${totalProcesses} have one or more ports not in the required state.`);
        }
      }
    });
  }


}
