import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { HistoryEntryDto, InstanceStateRecord } from 'src/app/models/gen.dtos';
import { InstanceStateService } from '../../services/instance-state.service';

@Component({
  selector: 'app-history-state-column',
  templateUrl: './history-state-column.component.html',
  styleUrls: ['./history-state-column.component.css'],
})
export class HistoryStateColumnComponent implements OnInit, OnDestroy {
  @Input() record: HistoryEntryDto;

  private states: InstanceStateRecord;
  private subscription: Subscription;
  /* template */ public stateTooltipText: string;
  /* template */ public stateClass: string[];
  /* template */ public stateIcon: string;

  constructor(private state: InstanceStateService) {
    this.subscription = this.state.state$.subscribe((s) => {
      this.states = s;
      this.ngOnInit(); // re-calculate
    });
  }

  ngOnInit(): void {
    this.stateTooltipText = this.getStateTooltip();
    this.stateClass = this.getStateClass();
    this.stateIcon = this.getStateIcon();
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  private getStateIcon() {
    if (this.states?.activeTag === this.record.instanceTag) {
      return 'check_circle'; // active
    } else if (
      this.states?.installedTags?.find((v) => v === this.record.instanceTag)
    ) {
      return 'check_circle_outline'; // installed
    }

    return null;
  }

  private getStateTooltip(): string {
    if (this.states?.activeTag === this.record.instanceTag) {
      return 'This version is active.'; // active
    } else if (
      this.states?.installedTags?.find((v) => v === this.record.instanceTag)
    ) {
      return 'This version is installed'; // installed
    }
  }

  private getStateClass(): string[] {
    if (this.states?.activeTag === this.record.instanceTag) {
      return ['material-symbols-filled'];
    }

    if (
      this.states?.installedTags?.find((v) => v === this.record.instanceTag)
    ) {
      // if the version is older than the last-active tag, we'll uninstall it later on.
      if (this.states?.lastActiveTag) {
        if (
          Number(this.states.lastActiveTag) > Number(this.record.instanceTag)
        ) {
          return ['local-greyed'];
        }
      }
    }
    return [];
  }
}
