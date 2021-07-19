import { Component, EventEmitter, Input, OnInit, Output, ViewChild } from '@angular/core';
import { SortDirection } from '@angular/material/sort';
import { BehaviorSubject } from 'rxjs';
import { BdDataColumn, bdDataDefaultSearch, bdDataDefaultSort, BdDataGrouping } from 'src/app/models/data';
import { BdDataGridComponent } from '../bd-data-grid/bd-data-grid.component';
import { BdDataTableComponent } from '../bd-data-table/bd-data-table.component';

@Component({
  selector: 'app-bd-data-display',
  templateUrl: './bd-data-display.component.html',
  styleUrls: ['./bd-data-display.component.css'],
})
export class BdDataDisplayComponent<T> implements OnInit {
  /**
   * The current display mode, which will either use bd-data-table (false) or bd-data-grid (true) to visualize data.
   */
  /* template */ _grid = false;
  @Input() set grid(val: boolean) {
    this._grid = val;

    // grid mode has no check support. clear check selection and disable check mode.
    this.checked = [];
    this.checkedChange.emit(this.checked);

    if (val) {
      this.checkMode = false;
    }
  }
  get grid() {
    return this._grid;
  }

  /**
   * Aria caption for the table, mainly for screen readers.
   */
  @Input() caption = 'Data Table';

  /**
   * The columns to display
   */
  @Input() columns: BdDataColumn<T>[];

  /**
   * A callback for sorting data by a certain column in a given direction.
   * This callback may be called multiple times for subsets of the data depending on the
   * current grouping of the view.
   *
   * Sorting through header click is disabled all together if this callback is not given.
   */
  @Input() sortData: (data: T[], column: BdDataColumn<T>, direction: SortDirection) => T[] = bdDataDefaultSort;

  /**
   * A callback which provides enhanced searching in the table. The default search will
   * concatenate each value in each row object, regardless of whether it is displayed or not.
   * Then the search string is applied to this single string in a case insensitive manner.
   */
  @Input() searchData: (search: string, data: T[], columns: BdDataColumn<T>[]) => T[] = bdDataDefaultSearch;

  /**
   * Whether the data-table should register itself as a BdSearchable with the global SearchService.
   */
  @Input() searchable = true;

  /**
   * A set of grouping definitions. The data will be grouped, each given definition represents a
   * level of grouping. Definitions are applied one after another, recursively.
   */
  @Input() grouping: BdDataGrouping<T>[];

  /**
   * The actual data. Arbitrary data which can be handled by the column definitions.
   */
  @Input() records: T[];

  /**
   * Whether the table should operate in checkbox-selection mode. Click events are not sent in this case.
   */
  @Input() checkMode = false;

  /**
   * Elements which should be checked.
   */
  @Input() checked: T[] = [];

  /**
   * If given, disables *all* checkboxes in check mode (including the header checkboxes) in case the value is true.
   */
  @Input() checkedFrozenWhen$: BehaviorSubject<boolean>;

  /**
   * A callback which can provide a route for each row. If given, each row will behave like a router link
   */
  @Input() recordRoute: (r: T) => any[];

  /**
   * Fires when the user changes the checked elements
   */
  @Output() checkedChange = new EventEmitter<T[]>();

  /**
   * Event fired if a record is clicked.
   */
  @Output() recordClick = new EventEmitter<T>();

  @ViewChild('table', { static: false }) tableComp: BdDataTableComponent<T>;
  @ViewChild('grid', { static: false }) gridComp: BdDataGridComponent<T>;

  constructor() {}

  ngOnInit(): void {}

  public update(): void {
    this.tableComp?.update();
    this.gridComp?.update();
  }
}
