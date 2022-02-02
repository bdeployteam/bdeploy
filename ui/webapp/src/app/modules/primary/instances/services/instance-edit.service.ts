import { moveItemInArray } from '@angular/cdk/drag-drop';
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { applyChangeset, Changeset, diff } from 'json-diff-ts';
import { cloneDeep, isEqual } from 'lodash-es';
import { BehaviorSubject, combineLatest, forkJoin, Observable, of, Subject } from 'rxjs';
import { debounceTime, finalize, first, tap } from 'rxjs/operators';
import { CLIENT_NODE_NAME } from 'src/app/models/consts';
import {
  ApplicationDescriptor,
  ApplicationDto,
  ApplicationType,
  ApplicationValidationDto,
  FileStatusDto,
  InstanceConfiguration,
  InstanceConfigurationDto,
  InstanceDto,
  InstanceNodeConfiguration,
  InstanceNodeConfigurationDto,
  InstanceUpdateDto,
  MinionDto,
  ProcessControlGroupConfiguration,
  ProductDto,
} from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { mapObjToArray } from 'src/app/modules/core/utils/object.utils';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { DEF_CONTROL_GROUP, getNodeOfApplication } from 'src/app/modules/panels/instances/utils/instance-utils';
import { GroupsService } from '../../groups/services/groups.service';
import { ProductsService } from '../../products/services/products.service';
import { InstancesService } from './instances.service';

export interface GlobalEditState {
  config: InstanceConfigurationDto;
  warnings: ApplicationValidationDto[];
  files: FileStatusDto[];
}

export enum ProcessEditState {
  ADDED = 'ADDED',
  CHANGED = 'CHANGED',
  INVALID = 'INVALID',
  NONE = 'NONE',
}

export interface Edit {
  /** Describes the changes done by this edit */
  description: string;

  /** Applies the changes to the current state */
  apply(current: GlobalEditState);
}

/**
 * Creates a new edit, capturing delta between base and state.
 *
 * Note that a 'virtual' edit may be created without looking at base/state. Any edit will be
 * applied after it has been created.
 */
export type EditFactory = (description: string, base: GlobalEditState, state: GlobalEditState) => Edit;

/** A generic edit, which may represent virtually any change. */
export class InstanceEdit implements Edit {
  private changeset: Changeset;

  constructor(public description: string, base: GlobalEditState, current: GlobalEditState) {
    // clone changeset to decouple from changes to the source object.
    this.changeset = cloneDeep(diff(base, current));
  }

  public apply(current: GlobalEditState) {
    // clone changeset to decouple from changes through applying *another* change.
    applyChangeset(current, cloneDeep(this.changeset));
  }
}

/**
 * A special edit which moves an application in the applications list.
 *
 * This is required to avoid *huge* diffs (add/remove) between applications. The huge diffs have the
 * *huge* drawback that null/undefined handling is extremely diffficult.
 */
export class InstanceApplicationMoveEdit implements Edit {
  constructor(
    public description: string,
    private nodeName: string,
    private previousIndex: number,
    private currentIndex: number,
    private source: string,
    private target: string
  ) {}

  public apply(current: GlobalEditState) {
    const nodeConfig = current.config.nodeDtos.find((n) => n.nodeName === this.nodeName).nodeConfiguration;

    // Step 1 - reorder in process control groups.
    if (this.source === this.target) {
      // can use a simple move in array.
      const orderList = nodeConfig.controlGroups.find((cg) => cg.name === this.source).processOrder;
      moveItemInArray(orderList, this.previousIndex, this.currentIndex);
    } else {
      // need to move from one array to another.
      const sourceList = nodeConfig.controlGroups.find((cg) => cg.name === this.source).processOrder;
      const targetList = nodeConfig.controlGroups.find((cg) => cg.name === this.target).processOrder;
      const item = sourceList[this.previousIndex];
      sourceList.splice(this.previousIndex, 1);
      targetList.splice(this.currentIndex, 0, item);
    }

    // Step 2 - reorder in applications.
    const allApps = ([] as string[]).concat(...nodeConfig.controlGroups.map((cg) => cg.processOrder));
    nodeConfig.applications.sort((a, b) => allApps.indexOf(a.uid) - allApps.indexOf(b.uid));
  }
}

@Injectable({
  providedIn: 'root',
})
export class InstanceEditService {
  public loading$ = new BehaviorSubject<boolean>(true);
  public saving$ = new BehaviorSubject<boolean>(false);

  public incompatible$ = new BehaviorSubject<boolean>(false);

  public undos: Edit[] = [];
  public redos: Edit[] = [];
  public base$ = new BehaviorSubject<GlobalEditState>(null);

  public undo$ = new BehaviorSubject<Edit>(null);
  public redo$ = new BehaviorSubject<Edit>(null);

  public state$ = new BehaviorSubject<GlobalEditState>(null);
  public nodes$ = new BehaviorSubject<{ [key: string]: MinionDto }>(null);
  public baseApplications$ = new BehaviorSubject<ApplicationDto[]>(null);
  public stateApplications$ = new BehaviorSubject<ApplicationDto[]>(null);

  public validationDebounce$ = new BehaviorSubject<any>(null);
  public validating$ = new BehaviorSubject<boolean>(false);
  public issues$ = new BehaviorSubject<ApplicationValidationDto[]>(null);
  private issuesSubject$ = new Subject<ApplicationValidationDto[]>();

  public current$ = new BehaviorSubject<InstanceDto>(null);
  public hasSaveableChanges$ = new BehaviorSubject<boolean>(false);
  public hasCurrentProduct$ = new BehaviorSubject<boolean>(false);

  private apiPath = (g) => `${this.cfg.config.api}/group/${g}/instance`;

  constructor(
    private http: HttpClient,
    private cfg: ConfigService,
    private groups: GroupsService,
    private instances: InstancesService,
    private products: ProductsService,
    private areas: NavAreasService
  ) {
    this.instances.current$.subscribe((instance) => {
      // ALWAYS update current$ to pick up updated global data (banner, etc.).
      const cur = this.current$.value;
      this.current$.next(instance);

      if (instance?.instance?.name !== cur?.instance?.name || instance?.instance?.tag !== cur?.instance?.tag) {
        if (!!this.undos?.length || !!this.redos?.length) {
          // instance (version?) changes, but we have edits... this means we're basically doomed.
          console.warn('Instance update with pending changes.');
          this.incompatible$.next(true);
        } else {
          // either nothing loaded yet, or we do not have any changes, in this case we can reset as well.
          this.reset();
        }
      }

      combineLatest([this.undo$, this.redo$, this.state$]).subscribe(() => {
        this.hasSaveableChanges$.next(this.hasSaveableChanges());
      });

      this.state$.subscribe(() => {
        this.hasCurrentProduct$.next(this.hasCurrentProduct());
      });
    });

    this.areas.panelRoute$.subscribe((route) => {
      if (this.isAllowEdit() && this.hasPendingChanges()) {
        console.warn('Unconcealed changes on route change, discarding.');
        this.discard();
      }
    });

    this.validationDebounce$.pipe(debounceTime(500)).subscribe((x) => this.validate());
    this.issues$.subscribe((i) => this.issuesSubject$.next(i));
  }

  /** Creates a standard edit factory, capturing changes by diffing as well as the current update warnings */
  public createStdEdit(): EditFactory {
    return (desc: string, base: GlobalEditState, state: GlobalEditState) => {
      return new InstanceEdit(desc, base, state);
    };
  }

  /** Creates a special edit factory which reorders applications on the given node */
  public createApplicationMove(node: string, previous: number, current: number, source: string, target: string): EditFactory {
    return (desc: string, base: GlobalEditState, state: GlobalEditState) => {
      return new InstanceApplicationMoveEdit(desc, node, previous, current, source, target);
    };
  }

  /**
   * Conceals the current differences to the base state as a single InstanceEdit.
   *
   * @param description a textual description of the changes.
   * @param factory an optional factory which is capable of creating an Edit.
   */
  public conceal(description: string, factory?: EditFactory): void {
    if (!this.isAllowEdit()) {
      console.error('Trying to edit with no current state');
      return;
    }

    // clear redo, no longer valid.
    this.redos = [];
    this.redo$.next(null);

    // the current state
    const state = this.reBuild();
    const fct = !!factory ? factory : this.createStdEdit();

    // and the diff to it :)
    const change = fct(description, state, this.state$.value);
    this.undos.push(change);
    this.undo$.next(change);

    // re-publish the state to inform others. need to re-build to assure proper null/undefined diffing.
    this.state$.next(this.reBuild());
    this.requestValidation();
  }

  public dismissUpdateIssues() {
    this.state$.value.warnings = null;
    this.conceal('Dismiss all update messages');
  }

  public dismissUpdateIssue(issue: ApplicationValidationDto) {
    this.state$.value.warnings?.splice(this.state$.value.warnings?.indexOf(issue), 1);
    this.conceal('Dismiss update message: ' + issue.message);
  }

  /**
   * Discards all changes and re-publishes base and state if possible.
   *
   * Note: this also discards all concealed (but not saved) edits.
   */
  public reset(): void {
    this.undos = [];
    this.redos = [];
    this.undo$.next(null);
    this.redo$.next(null);
    this.base$.next(null);
    this.state$.next(null);
    this.incompatible$.next(false);
    this.baseApplications$.next(null);
    this.stateApplications$.next(null);
    this.issues$.next(null);

    const inst = this.current$.value;
    if (!!inst) {
      this.loading$.next(true);
      forkJoin({
        nodes: this.instances.loadNodes(inst.instanceConfiguration.uuid, inst.instance.tag),
        minions: this.http.get<{ [key: string]: MinionDto }>(
          `${this.apiPath(this.groups.current$.value.name)}/${inst.instanceConfiguration.uuid}/${inst.instance.tag}/minionConfiguration`
        ),
      })
        .pipe(
          finalize(() => this.loading$.next(false)),
          measure('Load Node Configurations for Edit')
        )
        .subscribe(({ nodes, minions }) => {
          this.baseApplications$.next(nodes.applications);
          this.stateApplications$.next(nodes.applications);

          // we update the process control groups in case not present here. They will be saved with the next save or discarded silently.
          for (const nc of nodes.nodeConfigDtos) {
            this.getLastControlGroup(nc.nodeConfiguration); // implicitly updates if required.
          }

          const base: InstanceConfigurationDto = { config: inst.instanceConfiguration, nodeDtos: nodes.nodeConfigDtos };

          // make sure we have at least the master node if there is *no* node.
          const nodesArray = mapObjToArray(minions);
          if (!base.nodeDtos.length) {
            const masterNode = nodesArray.find((n) => n.value.master);
            if (!!masterNode) {
              base.nodeDtos.push(this.createEmptyNode(masterNode.key, base.config));
            }
          }

          // make sure we have the client application virtual node if there are client applications.
          const clientNode = base.nodeDtos.find((n) => n.nodeName === CLIENT_NODE_NAME);
          if (!clientNode && !!nodes.applications.find((a) => a.descriptor.type === ApplicationType.CLIENT)) {
            base.nodeDtos.push(this.createEmptyNode(CLIENT_NODE_NAME, base.config));
          }

          this.base$.next({ config: base, files: [], warnings: [] });
          this.state$.next(cloneDeep(this.base$.value));
          this.nodes$.next(minions);
        });
    }
  }

  /**
   * Discards current changes to the state by re-building the current state
   *
   * Note: This does *not* discard concealed changes.
   */
  public discard(): void {
    this.state$.next(this.reBuild());
  }

  /**
   * Determines whether there are pending changes which need to be concealed.
   *
   * @returns whether there are non-concealed changes.
   */
  public hasPendingChanges(): boolean {
    const concealed = this.reBuild();
    if (!concealed || !this.state$.value?.config) {
      return false;
    }
    return this.hasChanges(concealed.config, this.state$.value?.config);
  }

  /**
   * Determines whether there are concealed changes which can be saved to the server.
   *
   * @returns  whether there is one or more concealed changes.
   */
  private hasSaveableChanges(): boolean {
    return this.undos.length > 0 && this.hasChanges(this.state$.value, this.base$.value);
  }

  /** Applies all current InstanceEdits and saves the result to the server. */
  public save() {
    if (!this.isAllowEdit()) {
      console.error('Trying to save with no state');
      return of(null);
    }

    this.saving$.next(true);

    const state = this.reBuild();

    if (!this.hasChanges(state, this.base$.value)) {
      // we have literally undone all changes, so no need to save, we just reset.
      console.warn('No changes detected, you have logically undone all changes, not saving');
      this.reset();
      return of(null);
    }

    const managedServer = this.current$.value.managedServer?.hostName;
    const expect = this.current$.value.instance.tag;

    const update: InstanceUpdateDto = { config: state.config, files: state.files, validation: null };

    return this.http
      .post(`${this.apiPath(this.groups.current$.value.name)}/${state.config.config.uuid}/update`, update, { params: { managedServer, expect } })
      .pipe(
        finalize(() => this.saving$.next(false)),
        measure('Save Instance'),
        tap((_) => this.reset()) // success :) lets reset.
      );
  }

  /** Removes the last edit from the edit stack, effectively undoing it */
  public undo(): void {
    const change = this.undos.pop();
    if (!!change) {
      this.redos.push(change);
      this.redo$.next(change);
    }
    this.discard(); // rebuilds the current state.

    // now the next-oldest edit is the "current" one;
    if (!this.undos.length) {
      this.undo$.next(null);
    } else {
      this.undo$.next(this.undos[this.undos.length - 1]);
    }
  }

  /** Redoes an undone change if possible */
  public redo(): void {
    if (!this.redos.length) {
      return;
    }

    const change = this.redos.pop();
    this.undos.push(change);
    this.discard(); // rebuilds the current state.

    this.undo$.next(change);
    if (!this.redos.length) {
      this.redo$.next(null);
    } else {
      this.redo$.next(this.redos[this.redos.length - 1]);
    }
  }

  /** Retrieves the ApplicationDescriptor for the application with the given name in the current product. */
  public getApplicationDescriptor(name: string): ApplicationDescriptor {
    if (!this.stateApplications$.value) {
      return null;
    }

    return this.stateApplications$.value.find((a) => a.key.name === name)?.descriptor;
  }

  /** Creates an empty node, which can be added to an instance configuration */
  public createEmptyNode(name: string, instance: InstanceConfiguration): InstanceNodeConfigurationDto {
    return {
      nodeName: name,
      nodeConfiguration: {
        name: instance.name,
        autoStart: instance.autoStart,
        product: instance.product,
        purpose: instance.purpose,
        uuid: instance.uuid,
        applications: [],
        controlGroups: [cloneDeep(DEF_CONTROL_GROUP)],
      },
    };
  }

  public getProcessEditState(uid: string): ProcessEditState {
    // process UID must be unique across nodes, so we check all nodes for the process.
    const baseNodes = this.base$.value?.config?.nodeDtos;
    const stateNodes = this.state$.value?.config?.nodeDtos;

    if (!baseNodes || !stateNodes) {
      return ProcessEditState.NONE;
    }

    const baseNode = getNodeOfApplication(baseNodes, uid);
    const stateNode = getNodeOfApplication(stateNodes, uid);

    const baseNodeApps = baseNode?.nodeConfiguration?.applications;
    const stateNodeApps = stateNode?.nodeConfiguration?.applications;

    const baseAppIndex = baseNodeApps?.findIndex((a) => a.uid === uid);
    const stateAppIndex = stateNodeApps?.findIndex((a) => a.uid === uid);

    const baseAppCg = baseNode?.nodeConfiguration?.controlGroups?.find((cg) => cg.processOrder.includes(uid));
    const stateAppCg = stateNode?.nodeConfiguration?.controlGroups?.find((cg) => cg.processOrder.includes(uid));

    const baseAppCgIndex = baseAppCg?.processOrder?.findIndex((a) => a === uid);
    const stateAppCgIndex = stateAppCg?.processOrder?.findIndex((a) => a === uid);

    // if undefined, we just switch to "not found" - the node might not even exist.
    const baseComp = baseAppIndex === undefined || baseAppIndex === null ? -1 : baseAppIndex;
    const stateComp = stateAppIndex === undefined || stateAppIndex === null ? -1 : stateAppIndex;

    if (!!this.issues$.value?.length) {
      const issues = this.issues$.value.filter((i) => i.appUid === uid);
      if (!!issues?.length) {
        return ProcessEditState.INVALID;
      }
    }

    if (baseComp === -1 && stateComp === -1) {
      return ProcessEditState.NONE;
    } else if (baseComp !== -1 && stateComp === -1) {
      return ProcessEditState.NONE; // removed means it's no longer there - it has no state.
    } else if (baseComp === -1 && stateComp !== -1) {
      return ProcessEditState.ADDED;
    } else {
      // both are !== -1
      if (
        baseAppCg?.name === stateAppCg?.name &&
        baseAppCgIndex === stateAppCgIndex &&
        baseNode.nodeName === stateNode.nodeName &&
        !this.hasChanges(baseNodeApps[baseAppIndex], stateNodeApps[stateAppIndex])
      ) {
        return ProcessEditState.NONE;
      } else {
        return ProcessEditState.CHANGED;
      }
    }
  }

  public requestValidation(): Observable<ApplicationValidationDto[]> {
    this.validating$.next(true);
    this.validationDebounce$.next(null);
    return this.issuesSubject$.pipe(first());
  }

  private validate() {
    if (!this.state$.value?.config || !this.groups.current$.value?.name || !this.current$.value) {
      // re-request validation once loading is done...
      this.requestValidation();
      return;
    }

    // if the current product is not available, there is no way (currently) we can validate that...
    if (!this.current$.value.productDto) {
      this.validating$.next(false);
      return;
    }

    // don't validate config files in case we have large files.
    this.validating$.next(true);
    const upd: InstanceUpdateDto = { config: this.state$.value?.config, files: null, validation: null };
    this.http
      .post<ApplicationValidationDto[]>(`${this.apiPath(this.groups.current$.value.name)}/${this.state$.value?.config.config.uuid}/validate`, upd)
      .pipe(
        finalize(() => this.validating$.next(false)),
        measure('Validate Instance Configuration')
      )
      .subscribe((u) => this.issues$.next(!!u?.length ? u : null));
  }

  public updateProduct(target: ProductDto) {
    this.validating$.next(true);
    const upd: InstanceUpdateDto = { config: this.state$.value?.config, files: this.state$.value?.files, validation: null };
    forkJoin({
      update: this.http.post<InstanceUpdateDto>(
        `${this.apiPath(this.groups.current$.value.name)}/${this.state$.value?.config.config.uuid}/updateProductVersion/${target.key.tag}`,
        upd
      ),
      apps: this.products.loadApplications(target),
    })
      .pipe(
        finalize(() => this.validating$.next(false)),
        measure('Update Product Version')
      )
      .subscribe(({ update, apps }) => {
        this.stateApplications$.next(apps);
        this.state$.next({ config: update.config, files: update.files, warnings: !!update.validation?.length ? [...update.validation] : null });

        this.conceal(`Update Product Version to ${target.key.tag}`);
      });
  }

  public getLastControlGroup(node: InstanceNodeConfiguration): ProcessControlGroupConfiguration {
    if (!node.controlGroups?.length) {
      node.controlGroups = [cloneDeep(DEF_CONTROL_GROUP)];
      node.controlGroups[0].processOrder = node.applications.map((a) => a.uid);
    }
    return node.controlGroups[node.controlGroups.length - 1];
  }

  private hasCurrentProduct() {
    return !this.state$.value?.config?.config?.product?.name
      ? false
      : !!this.products.products$.value.find(
          (p) => p.key.name === this.state$.value.config.config.product.name && p.key.tag === this.state$.value.config.config.product.tag
        );
  }

  public getApplicationConfiguration(uid: string) {
    if (!this.state$.value?.config?.nodeDtos?.length) {
      return null;
    }
    return getNodeOfApplication(this.state$.value?.config?.nodeDtos, uid)?.nodeConfiguration.applications.find((a) => a.uid === uid);
  }

  /** Creates the current state from the base and all recorded edits. */
  private reBuild(): GlobalEditState {
    if (!this.base$.value) {
      return null;
    }
    const state = cloneDeep(this.base$.value);

    for (const change of this.undos) {
      change.apply(state);
    }

    return state;
  }

  private isAllowEdit(): boolean {
    if (!this.current$.value || !this.base$.value) {
      return false;
    }

    return !this.incompatible$.value;
  }

  private hasChanges(object: any, original: any) {
    if (!object || !original) {
      return false;
    }

    // since some code regards null/undefined as equivalent, objects may not have properties which were null previously as they are simply left out.
    return !isEqual(this.removeNullValues(object), this.removeNullValues(original));
  }

  /**
   * Creates a new object which contains all defined non-null properties of the original.
   *
   * @param obj the original object
   * @returns an object which contains all properties which are not null or undefined.
   */
  private removeNullValues(obj: any): any {
    if (typeof obj === 'object') {
      return Object.entries(obj)
        .filter(([k, v]) => ![null, undefined].includes(v))
        .reduce((r, [key, value]) => ({ ...r, [key]: this.removeNullValues(value) }), {});
    } else {
      return obj;
    }
  }
}
