import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { BehaviorSubject, isObservable, Observable, of } from 'rxjs';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { BdMicroIconButtonComponent } from '../../../../../core/components/bd-micro-icon-button/bd-micro-icon-button.component';
import { AsyncPipe, DatePipe } from '@angular/common';

export type StateType = 'ok' | 'info' | 'warning' | 'product' | 'update' | 'unknown';

export interface StateItem {
  name: string | Observable<string>;
  type: StateType | Observable<StateType>;
  tooltip?: string | Observable<string>;
  click?: () => void;
}

export interface StateItemToDisplay {
  name: Observable<string>;
  type: Observable<StateType>;
  tooltip?: Observable<string>;
  click?: () => void;
}

@Component({
    selector: 'app-node-state-panel',
    templateUrl: './state-panel.component.html',
    styleUrls: ['./state-panel.component.css'],
    imports: [MatTooltip, MatIcon, MatProgressSpinner, BdMicroIconButtonComponent, AsyncPipe, DatePipe]
})
export class NodeStatePanelComponent implements OnChanges {
  @Input() items: StateItem[];
  @Input() narrowWhen$: BehaviorSubject<boolean>;
  @Input() lastRefreshAt$: BehaviorSubject<number>;
  @Input() refreshingWhen$: BehaviorSubject<boolean>;

  @Output() manualRefresh = new EventEmitter<unknown>();

  protected itemsToDisplay: StateItemToDisplay[];

  ngOnChanges(changes: SimpleChanges) {
    if (changes['items']) {
      this.itemsToDisplay = [];
      changes['items'].currentValue.forEach((item) => {
        this.itemsToDisplay.push({
          name: this.makeObservable(item.name),
          type: this.makeObservable(item.type),
          tooltip: item.tooltip ? this.makeObservable(item.tooltip) : null,
          click: item.click ? item.click : null
        });
      });
    }
  }

  private makeObservable<T>(value: T | Observable<T>): Observable<T> {
    return isObservable(value) ? value : of(value);
  }
}
