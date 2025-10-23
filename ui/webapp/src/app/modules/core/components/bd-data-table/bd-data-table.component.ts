import { SelectionModel } from '@angular/cdk/collections';
import { CdkDragDrop, CdkDropList, CdkDrag, CdkDragHandle } from '@angular/cdk/drag-drop';
import { BreakpointObserver } from '@angular/cdk/layout';
import {
  AfterViewInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  EventEmitter,
  inject,
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
import { MatSort, Sort, SortDirection, MatSortHeader } from '@angular/material/sort';
import { DomSanitizer } from '@angular/platform-browser';
import { BehaviorSubject, debounceTime, Observable, of, Subject, Subscription } from 'rxjs';
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
import {
  MatTable,
  MatHeaderRowDef,
  MatHeaderRow,
  MatRowDef,
  MatRow,
  MatNoDataRow,
  MatFooterRowDef,
  MatFooterRow,
  MatColumnDef,
  MatHeaderCellDef,
  MatHeaderCell,
  MatFooterCellDef,
  MatFooterCell,
  MatCellDef,
  MatCell,
} from '@angular/material/table';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatTooltip } from '@angular/material/tooltip';
import { ClickStopPropagationDirective } from '../../directives/click-stop-propagation.directive';
import { NgClass, AsyncPipe } from '@angular/common';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { BdDataComponentCellComponent } from '../bd-data-component-cell/bd-data-component-cell.component';
import { BdButtonComponent } from '../bd-button/bd-button.component';
import { FlatTreeControl, FlatDataSource, TreeFlattener } from '../../utils/tree.utils';

/** Represents the hierarchical presentation of the records after grouping/sorting/searching is applied. */
interface Node<T> {
  nodeId: unknown;
  item: T;
  groupOrFirstColumn: unknown;
  children: Node<T>[];
  checkForbidden: boolean;
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

  sourceId: string;
  targetId: string;
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
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatTable,
    MatSort,
    CdkDropList,
    MatHeaderRowDef,
    MatHeaderRow,
    MatRowDef,
    MatRow,
    CdkDrag,
    RouterLink,
    RouterLinkActive,
    MatNoDataRow,
    MatFooterRowDef,
    MatFooterRow,
    MatColumnDef,
    MatHeaderCellDef,
    MatHeaderCell,
    MatSortHeader,
    MatTooltip,
    MatCheckbox,
    ClickStopPropagationDirective,
    MatFooterCellDef,
    MatFooterCell,
    MatCellDef,
    MatCell,
    NgClass,
    MatIconButton,
    MatIcon,
    CdkDragHandle,
    BdDataComponentCellComponent,
    BdButtonComponent,
    AsyncPipe,
  ],
})
export class BdDataTableComponent<T> implements OnInit, OnDestroy, AfterViewInit, OnChanges, BdSearchable {
  private readonly searchService = inject(SearchService);
  private readonly media = inject(BreakpointObserver);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly cd = inject(ChangeDetectorRef);

  /**
   * Aria caption for the table, mainly for screen readers.
   */
  @Input() caption = 'Data Table';

  /** An ID which is to be used for the table. This can be used to identify containers in drag-drop events. */
  @Input() id = 'bd-data-table';

  /** Maximum amount of rows that will be displayed to the user (for performance reasons) */
  @Input() maxRows = MAX_ROWS_PER_GROUP;

  /** If set the table will not exceed this height. It should also include a valid unit of measure. */
  @Input() maxHeight?: string = null;

  /**
   * The columns to display
   */
  protected _columns: BdDataColumn<T, unknown>[] = [];
  protected _visibleColumns: string[] = [];

  @Input() set columns(val: BdDataColumn<T, unknown>[]) {
    if (!val) {
      return;
    }
    // either unset or CARD is OK, only TABLE is not OK.
    this._columns = val.filter(
      (c) => !c.display || c.display === BdDataColumnDisplay.TABLE || c.display === BdDataColumnDisplay.BOTH
    );
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
  @Input() sortData: (data: T[], column: BdDataColumn<T, unknown>, direction: SortDirection) => T[] = bdDataDefaultSort;

  /**
   * A callback which provides enhanced searching in the table. The default search will
   * concatenate each value in each row object, regardless of whether it is displayed or not.
   * Then the search string is applied to this single string in a case insensitive manner.
   */
  @Input() searchData: (search: string, data: T[], columns: BdDataColumn<T, unknown>[]) => T[] = bdDataDefaultSearch;

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
   * A callback which can forbid a check state change.
   * checkChangeAllowed is invoked on checkbox click and is currently used to prompt user confirmation for selection
   * checkChangeForbidden is invoked during generateModel phase and should be used when we want to exclude certain records (and subsequently groups with header) from selection
   */
  @Input() checkChangeForbidden: (record: T) => boolean = () => false;

  /**
   * If given, disables *all* checkboxes in check mode (including the header checkboxes) in case the value is true.
   */
  @Input() checkedFrozenWhen$: BehaviorSubject<boolean>;

  /**
   * A callback which can provide a route for each row. If given, each row will behave like a router link
   */
  @Input() recordRoute: (r: T) => unknown[];

  /**
   * A list of connected drag-drop-enabled tables. Dragging is possible between those lists if given.
   */
  @Input() dragConnected: string[];

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
  private readonly sortHeader: MatSort;

  /** The current sort dictated by the sortHeader */
  @Input() sort: Sort;

  /** Hide the headers, shows only the contents area */
  @Input() headerHidden = false;

  /** Implicitly expand all groups when updating content */
  @Input() expandGroups = true;

  /** The current search/filter string given by onBdSearch */
  private search: string;

  /** Emitted when a redraw is requested. */
  private readonly redrawRequest$ = new Subject<unknown>();

  /** The treeControl provides the hierarchy and flattened nodes rendered by the table */
  treeControl = new FlatTreeControl<FlatNode<T>>(
    (node) => node.level,
    (node) => node.expandable
  );

  /** The transformer bound to 'this', so we can use this in the transformer function */
  private readonly boundTransformer: (node: Node<T>, level: number) => FlatNode<T> = this.transformer.bind(this);
  private readonly treeFlattener = new TreeFlattener(
    this.boundTransformer,
    (n) => n.level,
    (n) => n.expandable,
    (n) => n.children
  );
  private subscription: Subscription;
  private mediaSubscription: Subscription;

  /** endless pseudo-id counter for created nodes to be used with trackBy in case no ID column is set. */
  private nodeCnt = 0;

  protected hasMoreData = false;
  protected hasMoreDataText = '...';

  /** The model holding the current checkbox selection state */
  protected checkSelection = new SelectionModel<FlatNode<T>>(true);

  /** The flag that disables header checkbox in checkMode. Recalculated on every update */
  protected headerCheckForbidden = false;

  /** The data source used by the table - using the flattened hierarchy given by the treeControl */
  /* template */
  dataSource = new FlatDataSource(this.treeControl, this.treeFlattener);

  ngOnInit(): void {
    if (this.searchable) {
      // register this table as "searchable" in the global search service if requested.
      this.subscription = this.searchService.register(this);
    }

    this.redrawRequest$.pipe(debounceTime(200)).subscribe(() => {
      this.cd.detectChanges();
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
    this.closeMediaSubscriptions();
  }

  ngOnChanges(changes: SimpleChanges): void {
    // make sure that we update only if something changed which requires us to update :)
    // an update will re-create all content, so we want to avoid this as far as possible.
    if (
      (!!changes['records'] && !changes['records'].isFirstChange()) ||
      (!!changes['grouping'] && !changes['grouping'].isFirstChange())
    ) {
      this.update();
    }

    if (!!changes['checked'] && !changes['checked'].currentValue?.length) {
      this.checkSelection.clear();
    }
  }

  ngAfterViewInit(): void {
    this.sortHeader.sortChange.subscribe((s) => {
      this.sort = s;
      this.update();
    });

    // validate that input parameters are consistent and correct.
    if (this.dragReorderMode && (!!this.sortData || !!this.grouping?.length || this.checkMode)) {
      throw new Error(
        'Table drag-reorder mode may only be enabled when user-sorting, grouping and checking is disabled.'
      );
    }

    setTimeout(() => this.update());
  }

  private updateMediaSubscriptions() {
    this.closeMediaSubscriptions();
    this.mediaSubscription = new Subscription();
    this._columns
      .filter((c) => !!c.showWhen)
      .forEach((c) =>
        this.mediaSubscription.add(this.media.observe(c.showWhen).subscribe(() => this.updateColumnsToDisplay()))
      );
  }

  private closeMediaSubscriptions() {
    this.mediaSubscription?.unsubscribe();
  }

  private updateColumnsToDisplay() {
    this._visibleColumns = this._columns
      .filter((c) => {
        if (c.showWhen && !this.media.isMatched(c.showWhen)) {
          return false;
        }
        if (this.grouping && this.grouping.findIndex((g) => g.definition?.associatedColumn === c.id) !== -1) {
          return false;
        }
        return true;
      })
      .map((c) => c.id);

    this.cd.detectChanges();
  }

  bdOnSearch(value: string): void {
    this.search = value;
    // Whenever we perform a search/filter we clear all check selection.
    // This is to avoid having a check selection on a non-visible row.
    // We *don't* do this if there is a callback which may prevent deselection.
    if (!this.checkSelection.isEmpty() && !this.checkChangeAllowed) {
      this.checkSelection.clear();
      this.checkedChange.emit([]);
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
    this.dataSource.data = this.generateModel(this.records ? [...this.records] : [], this.grouping, this.sort, false);

    if (this.expandGroups) {
      // TODO: Saving of expansion state on update. To achieve this, every BdDataGrouping must
      // have a unique ID. This ID, along with the group name (which is shown in the first column)
      // can be used to remember the expansion state, using a SelectionModel just like check selection.
      this.treeControl.expandAll();
    }

    // if some node cannot be selected, when header selection should be disabled as well
    this.headerCheckForbidden = this.dataSource.data.some((node) => node.checkForbidden);

    // columns may change due to grouping.
    this.updateColumnsToDisplay();
    this.redraw();
  }

  /** Instructs Angular to detect changes in the underlying data *without* rebuilding the whole data. */
  public redraw() {
    this.redrawRequest$.next(true);
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
      level: level
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
  private generateModel(data: T[], grouping: BdDataGrouping<T>[], sort: Sort, skipSearch: boolean): Node<T>[] {
    // if there is grouping to be applied, apply the top-most level now, and recurse.
    if (!!grouping && grouping.length > 0) {
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

      // sort groups - sorting is dictated by the BdDataGrouping, or (if grouping does not specify) is natural.
      const byGroupSorted = new Map(
        [...byGroup.entries()].sort((a, b) => {
          if (grouping[0].definition.sort) {
            return grouping[0].definition.sort(a[0], b[0], a[1], b[1]);
          }
          return bdSortGroups(a[0], b[0]);
        })
      );

      // create nodes for groups, recurse grouping.
      const result: Node<T>[] = [];
      for (const [key, value] of byGroupSorted) {
        const searchMatchesGroup = this.search?.length > 0 && key.toLowerCase().includes(this.search.toLowerCase());
        const children = this.generateModel(value, grouping.slice(1), sort, skipSearch || searchMatchesGroup);
        if (children?.length) {
          result.push({
            nodeId: key,
            item: null,
            groupOrFirstColumn: key,
            children: children,
            checkForbidden: children.some((child) => child.checkForbidden)
          });
        }
      }
      return result;
    }

    // There is no grouping left, so we can now create the nodes for the actual data records.
    // The only thing left to do here is to apply the current searching/sorting if given. Otherwise
    // data is presented in the given order.
    let sortedData = skipSearch ? data : this.searchData(this.search, data, this._columns);
    if (!!this.sortData && !!sort && !!sort.active && !!sort.direction) {
      const col = this._columns.find((c) => c.id === sort.active);
      if (!col) {
        console.error('Cannot find active sort column ' + sort.active);
      } else {
        sortedData = this.sortData(sortedData, col, sort.direction);
      }
    }

    const idCols = this._columns?.filter((c) => c.isId);

    // last step is to transform the raw input data into Node<T> which is then further processed
    // by the transformer callback of treeControl.
    this.hasMoreData = sortedData.length > this.maxRows;
    return sortedData.slice(0, this.maxRows).map(
      (i) =>
        ({
          nodeId: idCols?.length ? idCols.map((c) => c.data(i)).join('_') : this.nodeCnt++,
          item: i,
          groupOrFirstColumn: this._columns[0].data(i),
          children: [],
          checkForbidden: this.checkChangeForbidden(i),
        } as Node<T>)
    );
  }

  protected trackNode(index: number, node: FlatNode<T>) {
    return node.node.nodeId;
  }

  protected getNoExpandIndent(level: number) {
    if (level === 0) {
      return 0;
    }
    return (level - 1) * 24 + 40;
  }

  protected getUnknownIcon(col: BdDataColumn<T, unknown>) {
    console.warn('No icon callback registered for column definition with action', col);
    return 'help'; // default fallback.
  }

  protected toggleCheck(node: FlatNode<T>, cb: MatCheckbox) {
    if (!node.expandable) {
      const target = !this.checkSelection.isSelected(node);
      let confirm = of(true);
      if (this.checkChangeAllowed) {
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

      if (isChecked) {
        // if ALL are checked, we deselect all,
        this.checkSelection.deselect(node);
      } else {
        // otherwise we "upgrade" to all selected
        this.checkSelection.select(node);
      }

      const children = this.treeControl.getDescendants(node);

      if (this.checkSelection.isSelected(node)) {
        this.checkSelection.select(...children);
      } else {
        this.checkSelection.deselect(...children);
      }
      this.checkedChange.emit(this.checkSelection.selected.filter((s) => !!s.node.item).map((s) => s.node.item));
    }
  }

  protected toggleCheckAll(cb: MatCheckbox) {
    const isChecked = this.isAnyChecked();

    if (isChecked) {
      this.checkSelection.deselect(...this.treeControl.dataNodes);
    } else {
      this.checkSelection.select(...this.treeControl.dataNodes);
    }

    this.checkedChange.emit(this.checkSelection.selected.filter((s) => !!s.node.item).map((s) => s.node.item));
    cb.checked = !isChecked;
  }

  protected isChecked(node: FlatNode<T>) {
    if (!node.expandable) {
      return this.checkSelection.isSelected(node);
    }

    const children = this.treeControl.getDescendants(node);
    return children.every((child) => this.checkSelection.isSelected(child));
  }

  protected isAllChecked() {
    return this.checkSelection.selected.filter((n) => !!n?.node?.item).length === this.records?.length;
  }

  protected isAnyChecked() {
    return this.checkSelection.selected.some((n) => !!n?.node?.item);
  }

  protected isPartiallyChecked(node: FlatNode<T>) {
    const children = this.treeControl.getDescendants(node);
    const selected = children.filter((child) => this.checkSelection.isSelected(child));
    return selected.length > 0 && selected.length < children.length; // at least one but not all.
  }

  protected isImageColumn(col: BdDataColumn<T, unknown>) {
    return col.hint === BdDataColumnTypeHint.AVATAR;
  }

  protected getImageUrl(col: BdDataColumn<T, unknown>, record: T) {
    const url = col.data(record);
    if (url && typeof url === 'string') {
      return this.sanitizer.bypassSecurityTrustUrl(url);
    }
    return null;
  }

  protected onDrop(event: CdkDragDrop<T[]>) {
    // we made sure during init that indices match (no sorting, no grouping, no checking), so we can be "pretty" sure that just passing indices is a good idea.
    this.dragReorder.emit({
      previousIndex: event.previousIndex,
      currentIndex: event.currentIndex,
      item: event.previousContainer.data[event.previousIndex],
      sourceId: event.previousContainer.id,
      targetId: event.container.id
    });
  }

  protected getDataAsStringFor(col: BdDataColumn<T, unknown>, record: T) {
    if (!!record && !!col.data(record)) {
      return col.data(record).toString();
    }

    return '';
  }

  protected getTooltipTextFor(col: BdDataColumn<T, unknown>, record: T) {
    if (!!record && !!col.tooltip) {
      return col.tooltip(record);
    }

    return this.getDataAsStringFor(col, record);
  }
}
