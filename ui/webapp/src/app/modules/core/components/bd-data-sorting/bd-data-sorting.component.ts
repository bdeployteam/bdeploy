import { Component, EventEmitter, Input, Output, ViewEncapsulation } from '@angular/core';
import { Sort, SortDirection } from '@angular/material/sort';
import { BdDataColumn } from 'src/app/models/data';
import { BdButtonPopupComponent } from '../bd-button-popup/bd-button-popup.component';
import { MatCard } from '@angular/material/card';
import { MatRadioGroup, MatRadioButton } from '@angular/material/radio';
import { FormsModule } from '@angular/forms';
import { MatButtonToggleGroup, MatButtonToggle } from '@angular/material/button-toggle';
import { MatIcon } from '@angular/material/icon';

@Component({
    selector: 'app-bd-data-sorting',
    templateUrl: './bd-data-sorting.component.html',
    styleUrls: ['./bd-data-sorting.component.css'],
    encapsulation: ViewEncapsulation.None,
    imports: [BdButtonPopupComponent, MatCard, MatRadioGroup, FormsModule, MatRadioButton, MatButtonToggleGroup, MatButtonToggle, MatIcon]
})
export class BdDataSortingComponent<T> {
  /**
   * The columns to select for sorting
   */
  protected _columns: BdDataColumn<T, unknown>[];

  @Input() set columns(cols: BdDataColumn<T, unknown>[]) {
    this._columns = cols.filter((col) => col.sortCard);
  }

  @Input() sort: Sort;
  @Output() sortChange = new EventEmitter<Sort>();

  @Input() disabled = false;

  protected direction: SortDirection;
  protected get selectedColumn(): BdDataColumn<T, unknown> {
    return this._columns?.find((col) => col.id === this.sort?.active);
  }

  get sortBy(): string {
    if (!this.selectedColumn || !this.sort?.direction) {
      return 'Sort By: None';
    }
    const column = this.selectedColumn.name;
    const direction = this.sort.direction === 'asc' ? '▴' : '▾';
    return `Sort By: ${column} ${direction}`;
  }

  updateSortColumn(col: BdDataColumn<T, unknown>): void {
    this.sort = { active: col.id, direction: this.sort?.direction };
    this.sortChange.emit(this.sort);
  }

  updateSortDirection(direction: SortDirection): void {
    this.sort = { active: this.sort?.active, direction };
    this.sortChange.emit(this.sort);
  }
}
