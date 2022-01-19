import { SelectionModel } from '@angular/cdk/collections';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { BreakpointObserver } from '@angular/cdk/layout';
import { FlatTreeControl } from '@angular/cdk/tree';
import {
  AfterViewInit,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
  SimpleChanges,
  ViewChild,
  ViewEncapsulation,
} from '@angular/core';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatSort, Sort, SortDirection } from '@angular/material/sort';
import { MatTreeFlatDataSource, MatTreeFlattener } from '@angular/material/tree';
import { DomSanitizer } from '@angular/platform-browser';
import { BehaviorSubject, Observable, of, Subscription } from 'rxjs';
import {
  BdDataColumn,
  BdDataColumnDisplay,
  BdDataColumnTypeHint,
  bdDataDefaultSearch,
  bdDataDefaultSort,
  BdDataGrouping,
  bdSortGroups,
  UNMATCHED_GROUP,
} from 'src/app/models/data';
import { BdSearchable, SearchService } from '../../services/search.service';
import { SettingsService } from '../../services/settings.service';

// member ordering due to default implementation for callbacks.
// tslint:disable: member-ordering

/** Represents the hirarchical presentation of the records after grouping/sorting/searching is applied. */
interface Node<T> {
  item: T;
  groupOrFirstColumn: any;
  children: Node<T>[];
}

/** Represents a flattened presentation of Node<T> which is used by the underlying control to render rows */
interface FlatNode<T> {
  node: Node<T>;
  expandable: boolean;
  level: number;
}

/** Represents a request to reorder an item from one index to another in the *original* input records */
export interface DragReorderEvent<T> {
  item: T;
  previousIndex: number;
  currentIndex: number;
}

const MAX_ROWS_PER_GROUP = 500;

/**
 * A table which renders generic data based on column descriptions. Supports:
 *  * Sorting
 *  * Grouping (multi-level)
 *  * Checkbox (multi-) selection
 *  * Dynamic column display (based on media queries)
 *  * Cell content data (string, number, etc.) or action (bd-button)
 *  * Single click row selection
 *  * Filtering (Searching, BdSearchable) with automatic SearchService registration
 */
@Component({
  selector: 'app-bd-data-table',
  templateUrl: './bd-data-table.component.html',
  styleUrls: ['./bd-data-table.component.css'],
  encapsulation: ViewEncapsulation.None,
})
export class BdDataTableComponent<T> implements OnInit, OnDestroy, AfterViewInit, OnChanges, BdSearchable {
  /**
   * Aria caption for the table, mainly for screen readers.
   */
  @Input() caption = 'Data Table';

  /**
   * The columns to display
   */
  /* template */ _columns: BdDataColumn<T>[];
  /* template */ _visibleColumns: string[];
  @Input() set columns(val: BdDataColumn<T>[]) {
    // either unset or CARD is OK, only TABLE is not OK.
    this._columns = val.filter((c) => !c.display || c.display === BdDataColumnDisplay.TABLE || c.display === BdDataColumnDisplay.BOTH);
    this.updateColumnsToDisplay();
    this.updateMediaSubscriptions();
  }

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
   * A callback which can allow/prevent a check state change to the target state.
   *
   * This is not supported for multi-select/deselect on group nodes.
   */
  @Input() checkChangeAllowed: (row: T, target: boolean) => Observable<boolean>;

  /**
   * If given, disables *all* checkboxes in check mode (including the header checkboxes) in case the value is true.
   */
  @Input() checkedFrozenWhen$: BehaviorSubject<boolean>;

  /**
   * A callback which can provide a route for each row. If given, each row will behave like a router link
   */
  @Input() recordRoute: (r: T) => any[];

  /**
   * Whether drag & drop re-ordering of rows is allowed.
   */
  @Input() dragReorderMode = false;

  /**
   * Fires when the user changes the checked elements
   */
  @Output() checkedChange = new EventEmitter<T[]>();

  /**
   * Event fired if a record is clicked.
   */
  @Output() recordClick = new EventEmitter<T>();

  /**
   * Event fired in dragReorderMode when the user drags and drops a record.
   */
  @Output() dragReorder = new EventEmitter<DragReorderEvent<T>>();

  /** The sort associated with the column headers */
  @ViewChild(MatSort)
  private sortHeader: MatSort;

  /** The current sort dicdated by the sortHeader */
  @Input() sort: Sort;

  /** Hide the headers, shows only the contents area */
  @Input() headerHidden = false;

  /** The current search/filter string given by onBdSearch */
  private search: string;

  /** The treeControl provides the hierarchy and flattened nodes rendered by the table */
  treeControl = new FlatTreeControl<FlatNode<T>>(
    (node) => node.level,
    (node) => node.expandable
  );

  /** The transformer bound to 'this', so we can use this in the transformer function */
  private boundTransformer: (node: Node<T>, level: number) => FlatNode<T> = this.transformer.bind(this);
  private treeFlattener = new MatTreeFlattener(
    this.boundTransformer,
    (n) => n.level,
    (n) => n.expandable,
    (n) => n.children
  );
  private subscription: Subscription;
  private mediaSubscription: Subscription;
  /* template */ hasMoreData = false;
  /* template */ hasMoreDataText = '...';

  /** The model holding the current checkbox selection state */
  /* template */ checkSelection = new SelectionModel<FlatNode<T>>(true);

  /** The data source used by the table - using the flattened hierarchy given by the treeControl */
  /* tempalte */ dataSource = new MatTreeFlatDataSource(this.treeControl, this.treeFlattener);

  constructor(private searchService: SearchService, private media: BreakpointObserver, private sanitizer: DomSanitizer, private settings: SettingsService) {}

  ngOnInit(): void {
    if (this.searchable) {
      // register this table as "searchable" in the global search service if requested.
      this.subscription = this.searchService.register(this);
    }
    this.subscription.add(
      this.settings.settingsUpdated$.subscribe((update) => {
        if (update) {
          this.update();
        }
      })
    );
  }

  ngOnDestroy(): void {
    if (!!this.subscription) {
      this.subscription.unsubscribe();
    }
    this.closeMediaSubscriptions();
  }

  ngOnChanges(changes: SimpleChanges): void {
    // make sure that we update only if something changed which requires us to update :)
    // an update will re-create all content, so we want to avoid this as far as possible.
    if ((!!changes['records'] && !changes['records'].isFirstChange()) || (!!changes['grouping'] && !changes['grouping'].isFirstChange())) {
      this.update();
    }
  }

  ngAfterViewInit(): void {
    this.sortHeader.sortChange.subscribe((s) => {
      this.sort = s;
      this.update();
    });

    // validate that input parameters are consistent and correct.
    if (this.dragReorderMode) {
      if (!!this.sortData || !!this.grouping?.length || this.checkMode) {
        throw new Error('Table drag-reorder mode may only be enabled when user-sorting, grouping and checking is disabled.');
      }
    }

    setTimeout(() => this.update());
  }

  private updateMediaSubscriptions() {
    this.closeMediaSubscriptions();
    this.mediaSubscription = new Subscription();
    this._columns
      .filter((c) => !!c.showWhen)
      .forEach((c) => this.mediaSubscription.add(this.media.observe(c.showWhen).subscribe((bs) => this.updateColumnsToDisplay())));
  }

  private closeMediaSubscriptions() {
    if (!!this.mediaSubscription) {
      this.mediaSubscription.unsubscribe();
    }
  }

  private updateColumnsToDisplay() {
    this._visibleColumns = this._columns
      .filter((c) => {
        if (!!c.showWhen) {
          if (!this.media.isMatched(c.showWhen)) {
            return false;
          }
        }
        return true;
      })
      .map((c) => c.id);
  }

  bdOnSearch(value: string): void {
    this.search = value;
    if (!this.checkSelection.isEmpty()) {
      // Whenever we perform a search/filter we clear all check selection.
      // This is to avoid having a check selection on a non-visible row.
      // We *don't* do this if there is a callback which may prevent deselection.
      if (!this.checkChangeAllowed) {
        this.checkSelection.clear();
        this.checkedChange.emit([]);
      }
    }
    this.update();
  }

  public update(): void {
    // the check selection will be restored based on this.checked during generateModel
    this.checkSelection.clear();

    // recreate the dataSource, applying sorting, filtering, grouping, etc.
    // benchmarks show that this method is quite fast, event with a lot of data.
    // it takes roughly 100 (76 - 110) ms to generate a model for ~1000 records.
    this.hasMoreData = false;
    this.dataSource.data = this.generateModel(this.searchData(this.search, !!this.records ? [...this.records] : [], this._columns), this.grouping, this.sort);

    // TODO: Saving of expansion state on update. To achieve this, every BdDataGrouping must
    // have a unique ID. This ID, along with the group name (which is shown in the first column)
    // can be used to remember the expansion state, using a SelectionModel just like check selection.
    this.treeControl.expandAll();
  }

  /**
   * Transforms a Node<T> (which is created by generateModel from the input data) into a
   * FlatNode<T> which is used for displaying in the actual underlying table.
   *
   * Transformation is controlled by the treeControl.
   *
   * WARNING: This method has to be bound to 'this' before using.
   */
  private transformer(node: Node<T>, level: number): FlatNode<T> {
    const expandable = !!node.children && node.children.length > 0;

    const flatNode = {
      node: node,
      expandable: expandable,
      level: level,
    };

    if (!!node.item && !!this.checked && !!this.checked.find((c) => c === node.item)) {
      this.checkSelection.select(flatNode);
    }

    return flatNode;
  }

  /**
   * Generates the actual model displayed by the widget from the raw data given.
   * This method is called recursively to apply groupings at various levels.
   */
  private generateModel(data: T[], grouping: BdDataGrouping<T>[], sort: Sort): Node<T>[] {
    // if there is grouping to be applied, apply the top-most level now, and recurse.
    if (!!grouping && grouping.length > 0) {
      const grp = grouping[0]; // apply the first grouping. later recurse and skip first level.

      // do grouping by identifying the "group" of each record through the BdDataGrouping.
      const byGroup = new Map<string, T[]>();
      for (const row of data) {
        let group = grouping[0].definition.group(row);

        if (!group) {
          group = UNMATCHED_GROUP;
        }

        const show = !grouping[0].selected?.length || grouping[0].selected.includes(group);
        if (show && group) {
          const list = byGroup.has(group) ? byGroup.get(group) : byGroup.set(group, []).get(group);
          list.push(row);
        }
      }

      // sort groups - sorting is dicdated by the BdDataGrouping, or (if grouping does not specify) is natural.
      const byGroupSorted = new Map(
        [...byGroup.entries()].sort((a, b) => {
          if (!!grouping[0].definition.sort) {
            return grouping[0].definition.sort(a[0], b[0]);
          }
          return bdSortGroups(a[0], b[0]);
        })
      );

      // create nodes for groups, recurse grouping.
      const result: Node<T>[] = [];
      for (const [key, value] of byGroupSorted) {
        const children = this.generateModel(value, grouping.slice(1), sort);
        if (!!children?.length) {
          result.push({
            item: null,
            groupOrFirstColumn: key,
            children: children,
          });
        }
      }
      return result;
    }

    // There is no grouping left, so we can now create the nodes for the actual data records.
    // The only thing left to do here is to apply the current sorting if given. Otherwise
    // data is presented in the given order.
    let sortedData = data;
    if (!!this.sortData && !!sort && !!sort.active && !!sort.direction) {
      const col = this._columns.find((c) => c.id === sort.active);
      if (!col) {
        console.error('Cannot find active sort column ' + sort.active);
      } else {
        sortedData = this.sortData(data, col, sort.direction);
      }
    }

    // last step is to transform the raw input data into Node<T> which is then further processed
    // by the transformer callback of treeControl.
    this.hasMoreData = sortedData.length > MAX_ROWS_PER_GROUP;
    return sortedData.slice(0, MAX_ROWS_PER_GROUP).map((i) => ({ item: i, groupOrFirstColumn: this._columns[0].data(i), children: [] }));
  }

  /* template */ getNoExpandIndent(level: number) {
    if (level === 0) {
      return 0;
    }
    return (level - 1) * 24 + 40;
  }

  /* template */ getUnknownIcon(col: BdDataColumn<T>) {
    console.warn('No icon callback registered for column definition with action', col);
    return 'help'; // default fallback.
  }

  /* template */ toggleCheck(node: FlatNode<T>, cb: MatCheckbox) {
    if (!node.expandable) {
      const target = !this.checkSelection.isSelected(node);
      let confirm = of(true);
      if (!!this.checkChangeAllowed) {
        confirm = this.checkChangeAllowed(node.node.item, target);
      }
      confirm.subscribe((ok) => {
        if (ok) {
          if (target) {
            this.checkSelection.select(node);
          } else {
            this.checkSelection.deselect(node);
          }
          this.checkedChange.emit(this.checkSelection.selected.filter((s) => !!s.node.item).map((s) => s.node.item));
        } else {
          cb.checked = !target;
        }
      });
    } else {
      const isChecked = this.isChecked(node);

      // if ALL are checked, we deselect all, otherwise we "upgrade" to all selected
      isChecked ? this.checkSelection.deselect(node) : this.checkSelection.select(node);

      const children = this.treeControl.getDescendants(node);
      this.checkSelection.isSelected(node) ? this.checkSelection.select(...children) : this.checkSelection.deselect(...children);
      this.checkedChange.emit(this.checkSelection.selected.filter((s) => !!s.node.item).map((s) => s.node.item));
    }
  }

  /* template */ toggleCheckAll(cb: MatCheckbox) {
    const isChecked = this.isAnyChecked();
    isChecked ? this.checkSelection.deselect(...this.treeControl.dataNodes) : this.checkSelection.select(...this.treeControl.dataNodes);
    this.checkedChange.emit(this.checkSelection.selected.filter((s) => !!s.node.item).map((s) => s.node.item));
  }

  /* template */ isChecked(node: FlatNode<T>) {
    if (!node.expandable) {
      return this.checkSelection.isSelected(node);
    }

    const children = this.treeControl.getDescendants(node);
    return children.every((child) => this.checkSelection.isSelected(child));
  }

  /* template */ isAllChecked() {
    return this.checkSelection.selected.filter((n) => !!n?.node?.item).length === this.records?.length;
  }

  /* template */ isAnyChecked() {
    return this.checkSelection.selected.filter((n) => !!n?.node?.item).length > 0;
  }

  /* template */ isPartiallyChecked(node: FlatNode<T>) {
    const children = this.treeControl.getDescendants(node);
    const selected = children.filter((child) => this.checkSelection.isSelected(child));
    return selected.length > 0 && selected.length < children.length; // at least one but not all.
  }

  /* template */ isImageColumn(col: BdDataColumn<T>) {
    return col.hint === BdDataColumnTypeHint.AVATAR;
  }

  /* template */ getImageUrl(col: BdDataColumn<T>, record: T) {
    const url = col.data(record);
    if (!!url) {
      return this.sanitizer.bypassSecurityTrustUrl(url);
    }
  }

  /* template */ onDrop(event: CdkDragDrop<any>) {
    // we made sure during init that indices match (no sorting, no grouping, no checking), so we can be "pretty" sure that just passing indices is a good idea.
    this.dragReorder.emit({ previousIndex: event.previousIndex, currentIndex: event.currentIndex, item: this.records[event.previousIndex] });
  }
}
