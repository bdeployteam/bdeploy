import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { BdDataColumn, BdDataColumnDisplay, bdDataDefaultSearch, BdDataGrouping } from 'src/app/models/data';
import { LoggingService } from '../../services/logging.service';

@Component({
  selector: 'app-bd-data-grid',
  templateUrl: './bd-data-grid.component.html',
  styleUrls: ['./bd-data-grid.component.css'],
})
export class BdDataGridComponent<T> implements OnInit {
  private log = this.logging.getLogger('BdDataTableComponent');

  /**
   * The columns to display
   */
  /* template */ _columns: BdDataColumn<T>[];
  @Input() set columns(val: BdDataColumn<T>[]) {
    // either unset or CARD is OK, only TABLE is not OK.
    this._columns = val.filter((c) => c.display !== BdDataColumnDisplay.TABLE);
  }

  /**
   * A callback which provides enhanced searching in the table. The default search will
   * concatenate each value in each row object, regardless of whether it is displayed or not.
   * Then the search string is applied to this single string in a case insensitive manner.
   */
  @Input() searchData: (search: string, data: T[]) => T[] = bdDataDefaultSearch;

  /**
   * Whether the data-table should register itself as a BdSearchable with the global SearchService.
   */
  @Input() searchable = true;

  /**
   * A single grouping definition. The data will be grouped according to this definition.
   * Multiple grouping is not supported by the grid.
   */
  @Input() grouping: BdDataGrouping<T>;

  /**
   * The actual data. Arbitrary data which can be handled by the column definitions.
   */
  @Input() records: T[];

  /**
   * Event fired if a record is clicked.
   */
  @Output() recordClick = new EventEmitter<T>();

  // TODO: Grouping
  // TODO: Search/Filter

  constructor(private logging: LoggingService) {}

  ngOnInit(): void {}
}
