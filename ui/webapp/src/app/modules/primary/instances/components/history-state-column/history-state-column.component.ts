import { Component, inject, Input, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { HistoryEntryDto, InstanceStateRecord } from 'src/app/models/gen.dtos';
import { InstanceStateService } from '../../services/instance-state.service';
import { MatIcon } from '@angular/material/icon';
import { NgClass } from '@angular/common';
import { MatTooltip } from '@angular/material/tooltip';
import { BdDataColumn } from '../../../../../models/data';
import { CellComponent } from '../../../../core/components/bd-data-component-cell/bd-data-component-cell.component';

@Component({
    selector: 'app-history-state-column',
    templateUrl: './history-state-column.component.html',
    styleUrls: ['./history-state-column.component.css'],
    imports: [MatIcon, NgClass, MatTooltip]
})
export class HistoryStateColumnComponent implements OnInit, OnChanges, OnDestroy, CellComponent<HistoryEntryDto, string> {
  private readonly state = inject(InstanceStateService);

  @Input() record: HistoryEntryDto;
  @Input() column: BdDataColumn<HistoryEntryDto, string>;

  private states: InstanceStateRecord;
  private subscription: Subscription;
  protected stateTooltipText: string;
  protected stateClass: string[];
  protected stateIcon: string;

  ngOnInit(): void {
    this.subscription = this.state.state$.subscribe((s) => {
      this.states = s;
      this.ngOnChanges(); // re-calculate
    });

    this.ngOnChanges();
  }

  ngOnChanges(): void {
    if (this.record) {
      this.stateTooltipText = this.getStateTooltip();
      this.stateClass = this.getStateClass();
      this.stateIcon = this.getStateIcon();
    }
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  private getStateIcon() {
    if (this.states?.activeTag === this.record.instanceTag) {
      return 'check_circle'; // active
    } else if (this.states?.installedTags?.find((v) => v === this.record.instanceTag)) {
      return 'check_circle_outline'; // installed
    }

    return null;
  }

  private getStateTooltip(): string {
    if (this.states?.activeTag === this.record.instanceTag) {
      return 'This version is active.'; // active
    } else if (this.states?.installedTags?.find((v) => v === this.record.instanceTag)) {
      return 'This version is installed'; // installed
    }
    return null;
  }

  private getStateClass(): string[] {
    if (this.states?.activeTag === this.record.instanceTag) {
      return ['material-symbols-filled'];
    }

    if (this.states?.installedTags?.find((v) => v === this.record.instanceTag)) {
      // if the version is older than the last-active tag, we'll uninstall it later on.
      if (this.states?.lastActiveTag && Number(this.states.lastActiveTag) > Number(this.record.instanceTag)) {
        return ['local-greyed'];
      }
    }
    return [];
  }
}
