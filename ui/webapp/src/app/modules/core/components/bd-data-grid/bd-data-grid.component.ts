import {
  Component,
  ContentChild,
  EventEmitter,
  inject,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
  SimpleChanges,
  TemplateRef
} from '@angular/core';
import { Sort, SortDirection } from '@angular/material/sort';
import { BehaviorSubject, Observable, of, Subscription } from 'rxjs';
import {
  BdDataColumn,
  BdDataColumnDisplay,
  bdDataDefaultSearch,
  bdDataDefaultSort,
  BdDataGrouping,
  bdExtractGroups,
  UNMATCHED_GROUP
} from 'src/app/models/data';
import { NavAreasService } from '../../services/nav-areas.service';
import { BdSearchable, SearchService } from '../../services/search.service';
import { MatTabGroup, MatTab, MatTabChangeEvent } from '@angular/material/tabs';
import { BdDataCardComponent } from '../bd-data-card/bd-data-card.component';
import { NgClass, NgTemplateOutlet, AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-bd-data-grid',
    templateUrl: './bd-data-grid.component.html',
    imports: [MatTabGroup, MatTab, BdDataCardComponent, NgClass, NgTemplateOutlet, AsyncPipe]
})
export class BdDataGridComponent<T> implements OnInit, OnDestroy, BdSearchable, OnChanges {
  private readonly searchService = inject(SearchService);
  protected readonly areas = inject(NavAreasService);

  /**
   * The columns to display
   */
  protected _columns: BdDataColumn<T>[];
  @Input() set columns(val: BdDataColumn<T>[]) {
    // either unset or CARD is OK, only TABLE is not OK.
    this._columns = val.filter(
      (c) => !c.display || c.display === BdDataColumnDisplay.CARD || c.display === BdDataColumnDisplay.BOTH
    );
  }

  /**
   * A callback which provides enhanced searching in the grid. The default search will
   * concatenate each value in each record object, regardless of whether it is displayed or not.
   * Then the search string is applied to this single string in a case insensitive manner.
   */
  @Input() searchData: (search: string, data: T[], columns: BdDataColumn<T>[]) => T[] = bdDataDefaultSearch;

  /**
   * Whether the data-grid should register itself as a BdSearchable with the global SearchService.
   */
  @Input() searchable = true;

  /**
   * Holds the search string from global filter
   */
  private search: string;

  /**
   * A callback for sorting data by a certain column in a given direction.
   * This callback may be called multiple times for subsets of the data depending on the
   * current grouping of the view.
   *
   * Sorting through header click is disabled all together if this callback is not given.
   */
  @Input() sortData: (data: T[], column: BdDataColumn<T>, direction: SortDirection) => T[] = bdDataDefaultSort;

  /**
   * Which column to sort cards by
   */
  @Input() sort: Sort;

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
   * Whether the table should operate in checkbox-selection mode. Click events are not sent in this case.
   */
  @Input() checkMode = false;

  /**
   * Elements which should be checked.
   */
  @Input() checked: T[] = [];

  /**
   * A callback which can allow/prevent a check state change to the target state.
   */
  @Input() checkChangeAllowed: (record: T, target: boolean) => Observable<boolean>;

  /**
   * A callback which can forbid a check state change.
   */
  @Input() checkChangeForbidden: (record: T) => boolean = () => false;

  /**
   * If given, disables check selection in case the value is true.
   */
  @Input() checkedFrozenWhen$: BehaviorSubject<boolean>;

  /**
   * A callback which can provide a route for each row. If given, each row will behave like a router link
   */
  @Input() recordRoute: (r: T) => unknown[];

  /**
   * Fires when the user changes the checked elements
   */
  @Output() checkedChange = new EventEmitter<T[]>();

  /**
   * Event fired if a record is clicked.
   */
  @Output() recordClick = new EventEmitter<T>();

  /*template*/
  recordsToDisplay$ = new BehaviorSubject<T[]>([]);
  /*template*/
  groupValues: string[];
  private activeGroup: string;

  private subscription: Subscription;

  @ContentChild('dataGridExtraCardDetails')
  dataGridExtraCardDetails: TemplateRef<unknown>;

  ngOnInit(): void {
    if (this.searchable) {
      // register this table as "searchable" in the global search service if requested.
      this.subscription = this.searchService.register(this);
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['records'] || changes['grouping']) {
      this.populateRecords();
    }
    if (changes['sort']) {
      this.calculateRecordsToDisplay();
    }
  }

  protected onTabChange(event: MatTabChangeEvent) {
    this.activeGroup = event.tab?.textLabel;
    this.calculateRecordsToDisplay();
  }

  protected onRecordClick(event: T) {
    this.recordClick.emit(event);

    if (!this.checkMode) return;

    if (this.checkedFrozenWhen$?.value) return;

    if (this.checkChangeForbidden?.(event)) return;

    const isChecked = this.checked.some((record) => record === event);

    let confirm = of(true);
    if (this.checkChangeAllowed) {
      confirm = this.checkChangeAllowed(event, !isChecked);
    }
    confirm.subscribe((ok) => {
      if (ok) {
        const next = isChecked ? this.checked.filter((record) => record !== event) : [...this.checked, event];
        this.checkedChange.emit(next);
      }
    });
  }

  protected isSelected(record: T): boolean {
    return this.checkMode && this.checked.some((r) => r === record);
  }

  private populateRecords(): void {
    // populate records to display with empty search by default.
    if (this.grouping) {
      this.groupValues = bdExtractGroups(this.grouping.definition, this.records);
      this.activeGroup = this.groupValues[0];
    } else {
      this.activeGroup = null;
    }
    this.bdOnSearch(null);
  }

  public update(): void {
    this.calculateRecordsToDisplay();
  }

  bdOnSearch(search: string) {
    this.search = search;
    this.calculateRecordsToDisplay();
  }

  private calculateRecordsToDisplay() {
    let records = this.searchData(this.search, this.records, this._columns);
    records = this.getGroupRecords(records, this.activeGroup);
    records = this.sortRecords(records);
    this.recordsToDisplay$.next(records);
  }

  private sortRecords(records: T[]): T[] {
    if (!this.sortData || !this.sort?.active || !this.sort.direction) {
      return records;
    }
    const col = this._columns.find((c) => c.id === this.sort.active);
    if (!col) {
      console.error('Cannot find active sort column ' + this.sort.active);
      return records;
    }
    return this.sortData(records, col, this.sort.direction);
  }

  private getGroupRecords(records: T[], group: string): T[] {
    if (!group) {
      return records;
    }
    return records.filter((r) => {
      const grp = this.grouping?.definition?.group(r);
      if (!grp && group === UNMATCHED_GROUP) {
        return true;
      }
      return !group || grp === group;
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }
}
