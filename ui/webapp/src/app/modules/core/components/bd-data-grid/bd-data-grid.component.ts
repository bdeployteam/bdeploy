import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges } from '@angular/core';
import { max } from 'lodash-es';
import { BehaviorSubject, Subscription } from 'rxjs';
import { BdDataColumn, BdDataColumnDisplay, bdDataDefaultSearch, BdDataGrouping, bdExtractGroups, UNMATCHED_GROUP } from 'src/app/models/data';
import { NavAreasService } from '../../services/nav-areas.service';
import { BdSearchable, SearchService } from '../../services/search.service';

@Component({
  selector: 'app-bd-data-grid',
  templateUrl: './bd-data-grid.component.html',
  styleUrls: ['./bd-data-grid.component.css'],
})
export class BdDataGridComponent<T> implements OnInit, OnDestroy, BdSearchable, OnChanges {
  /**
   * The columns to display
   */
  /* template */ _columns: BdDataColumn<T>[];
  @Input() set columns(val: BdDataColumn<T>[]) {
    // either unset or CARD is OK, only TABLE is not OK.
    this._columns = val.filter((c) => !c.display || c.display === BdDataColumnDisplay.CARD || c.display === BdDataColumnDisplay.BOTH);
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
   * A single grouping definition. The data will be grouped according to this definition.
   * Multiple grouping is not supported by the grid.
   */
  @Input() grouping: BdDataGrouping<T>;

  /**
   * The actual data. Arbitrary data which can be handled by the column definitions.
   */
  @Input() records: T[];

  /**
   * A callback which can provide a route for each row. If given, each row will behave like a router link
   */
  @Input() recordRoute: (r: T) => any[];

  /**
   * Event fired if a record is clicked.
   */
  @Output() recordClick = new EventEmitter<T>();

  /*template*/ recordsToDisplay$ = new BehaviorSubject<T[]>([]);
  /*template*/ groupValues: string[];
  /*template*/ groupRecords;
  /*template*/ ltSm: string;
  /*template*/ sm: string;
  /*template*/ md: string;
  /*template*/ lg: string;
  /*template*/ gtLg: string;
  private activeGroup: string;

  private subscription: Subscription;

  constructor(private searchService: SearchService, public areas: NavAreasService) {}

  ngOnInit(): void {
    if (this.searchable) {
      // register this table as "searchable" in the global search service if requested.
      this.subscription = this.searchService.register(this);
    }

    this.subscription.add(
      this.areas.panelVisible$.subscribe((panelVisible) => {
        this.ltSm = this.getFlexAmount(1, panelVisible);
        this.sm = this.getFlexAmount(2, panelVisible);
        this.md = this.getFlexAmount(3, panelVisible);
        this.lg = this.getFlexAmount(4, panelVisible);
        this.gtLg = this.getFlexAmount(5, panelVisible);
      })
    );
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.records || changes.grouping) {
      this.populateRecords();
    }
  }

  private populateRecords(): void {
    // populate records to display with empty search by default.
    this.bdOnSearch(null);
    if (this.grouping) {
      this.groupValues = bdExtractGroups(this.grouping.definition, this.records);
      this.activeGroup = this.groupValues[0];
      this.groupRecords = this.getGroupRecords(this.activeGroup);
    }
  }

  ngOnDestroy(): void {
    if (!!this.subscription) {
      this.subscription.unsubscribe();
    }
  }

  public update(): void {
    this.recordsToDisplay$.next(this.recordsToDisplay$.value);
  }

  bdOnSearch(search: string) {
    this.recordsToDisplay$.next(this.searchData(search, this.records, this._columns));
    if (this.activeGroup) {
      this.groupRecords = this.getGroupRecords(this.activeGroup);
    }
  }

  // TODO: Remove if it's unused
  /* template */ getGroupValues() {
    return bdExtractGroups(this.grouping.definition, this.records);
  }

  private getGroupRecords(group) {
    return this.recordsToDisplay$.value.filter((r) => {
      const grp = this.grouping.definition.group(r);
      if (!grp && group === UNMATCHED_GROUP) {
        return true;
      }
      return grp === group;
    });
  }

  /* template */ onTabChange(event) {
    this.activeGroup = event.tab?.textLabel;
    if (this.activeGroup) {
      this.groupRecords = this.getGroupRecords(this.activeGroup);
    }
  }

  private getFlexAmount(numCards: number, panelVisible: boolean) {
    if (panelVisible) {
      numCards = max([1, numCards - 1]);
    }
    return `0 0 ${100 / numCards}%`;
  }
}
