import { CollectionViewer, DataSource, SelectionModel } from '@angular/cdk/collections';
import { BehaviorSubject, merge, Observable } from 'rxjs';
import { map, take } from 'rxjs/operators';

/**
 * Flat tree control used by bd-data-table to handle node expansion,
 * and in the data source to handle a tree after it was flattened.
 */
export class FlatTreeControl<T> {
  dataNodes: T[];

  expansionModel: SelectionModel<unknown> = new SelectionModel<unknown>(true);

  readonly getChildren: (dataNode: T) => Observable<T[]> | T[] | undefined | null;

  constructor(
    public getLevel: (dataNode: T) => number,
    public isExpandable: (dataNode: T) => boolean,
    // identity function for the node
    public trackBy?: (dataNode: T) => unknown
  ) {}

  /**
   * Identify all descendants and return them as a list.
   *
   * The `dataNodes` must be set to a flattened version.
   */
  getDescendants(dataNode: T): T[] {
    const startIndex = this.dataNodes.indexOf(dataNode);
    const results: T[] = [];

    // Goes through the array of flattened nodes, and determines if the node
    // is a descendant based on level.
    for (
      let i = startIndex + 1;
      i < this.dataNodes.length && this.getLevel(dataNode) < this.getLevel(this.dataNodes[i]);
      i++
    ) {
      results.push(this.dataNodes[i]);
    }
    return results;
  }

  expandAll(): void {
    this.expansionModel.select(...this.dataNodes.map((node) => this._trackByValue(node)));
  }

  toggle(dataNode: T): void {
    this.expansionModel.toggle(this._trackByValue(dataNode));
  }

  expand(dataNode: T): void {
    this.expansionModel.select(this._trackByValue(dataNode));
  }

  collapse(dataNode: T): void {
    this.expansionModel.deselect(this._trackByValue(dataNode));
  }

  isExpanded(dataNode: T): boolean {
    return this.expansionModel.isSelected(this._trackByValue(dataNode));
  }

  toggleDescendants(dataNode: T): void {
    if (this.expansionModel.isSelected(this._trackByValue(dataNode))) {
      this.collapseDescendants(dataNode);
    } else {
      this.expandDescendants(dataNode);
    }
  }

  collapseAll(): void {
    this.expansionModel.clear();
  }

  expandDescendants(dataNode: T): void {
    const toBeProcessed = [dataNode];
    toBeProcessed.push(...this.getDescendants(dataNode));
    this.expansionModel.select(...toBeProcessed.map((value) => this._trackByValue(value)));
  }

  collapseDescendants(dataNode: T): void {
    const toBeProcessed = [dataNode];
    toBeProcessed.push(...this.getDescendants(dataNode));
    this.expansionModel.deselect(...toBeProcessed.map((value) => this._trackByValue(value)));
  }

  protected _trackByValue(value: T | unknown): unknown {
    return this.trackBy ? this.trackBy(value as T) : value;
  }
}

/**
 * Tree flattener to convert a normal type of node to a flat node.
 * Transform nested nodes of type `T` to flattened nodes of type `F`.
 */
export class TreeFlattener<T, F> {
  constructor(
    public transformFunction: (node: T, level: number) => F,
    public getLevel: (node: F) => number,
    public isExpandable: (node: F) => boolean,
    public getChildren: (node: T) => Observable<T[]> | T[] | undefined | null
  ) {}

  _flattenNode(node: T, level: number, resultNodes: F[], parentMap: boolean[]): F[] {
    const flatNode = this.transformFunction(node, level);
    resultNodes.push(flatNode);

    if (this.isExpandable(flatNode)) {
      const childrenNodes = this.getChildren(node);
      if (childrenNodes) {
        if (Array.isArray(childrenNodes)) {
          this._flattenChildren(childrenNodes, level, resultNodes, parentMap);
        } else {
          childrenNodes.pipe(take(1)).subscribe((children) => {
            this._flattenChildren(children, level, resultNodes, parentMap);
          });
        }
      }
    }
    return resultNodes;
  }

  _flattenChildren(children: T[], level: number, resultNodes: F[], parentMap: boolean[]): void {
    children.forEach((child, index) => {
      const childParentMap: boolean[] = parentMap.slice();
      childParentMap.push(index !== children.length - 1);
      this._flattenNode(child, level + 1, resultNodes, childParentMap);
    });
  }

  /**
   * Flatten a list of node type T to flattened version of node F.
   */
  flattenNodes(structuredData: T[]): F[] {
    const resultNodes: F[] = [];
    structuredData.forEach((node) => this._flattenNode(node, 0, resultNodes, []));
    return resultNodes;
  }

  /**
   * Expand flattened node with current expansion status.
   * The returned list may have different length.
   */
  expandFlattenedNodes(nodes: F[], treeControl: FlatTreeControl<F>): F[] {
    const results: F[] = [];
    const currentExpand: boolean[] = [];
    currentExpand[0] = true;

    nodes.forEach((node) => {
      let expand = true;
      for (let i = 0; i <= this.getLevel(node); i++) {
        expand = expand && currentExpand[i];
      }
      if (expand) {
        results.push(node);
      }
      if (this.isExpandable(node)) {
        currentExpand[this.getLevel(node) + 1] = treeControl.isExpanded(node);
      }
    });
    return results;
  }
}

/**
 * The nested tree nodes of type `T` are flattened through `TreeFlattener` and converted
 * to type `F`.
 */
export class FlatDataSource<T, F> extends DataSource<F> {
  private readonly _flattenedData = new BehaviorSubject<F[]>([]);
  private readonly _expandedData = new BehaviorSubject<F[]>([]);

  get data() {
    return this._data.value;
  }

  set data(value: T[]) {
    this._data.next(value);
    this._flattenedData.next(this._treeFlattener.flattenNodes(this.data));
    this._treeControl.dataNodes = this._flattenedData.value;
  }

  private readonly _data = new BehaviorSubject<T[]>([]);

  constructor(
    private readonly _treeControl: FlatTreeControl<F>,
    private readonly _treeFlattener: TreeFlattener<T, F>,
    initialData?: T[]
  ) {
    super();

    if (initialData) {
      this.data = initialData;
    }
  }

  connect(collectionViewer: CollectionViewer): Observable<F[]> {
    return merge(collectionViewer.viewChange, this._treeControl.expansionModel.changed, this._flattenedData).pipe(
      map(() => {
        this._expandedData.next(this._treeFlattener.expandFlattenedNodes(this._flattenedData.value, this._treeControl));
        return this._expandedData.value;
      })
    );
  }

  disconnect() {
    // no op
  }
}
