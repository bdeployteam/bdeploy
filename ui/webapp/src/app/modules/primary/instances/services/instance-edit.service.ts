import { moveItemInArray } from '@angular/cdk/drag-drop';
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { applyChangeset, Changeset, diff } from 'json-diff-ts';
import { cloneDeep, isMatchWith, isNil } from 'lodash-es';
import { BehaviorSubject, forkJoin } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { CLIENT_NODE_NAME } from 'src/app/models/consts';
import {
  ApplicationDescriptor,
  ApplicationDto,
  ApplicationType,
  InstanceConfiguration,
  InstanceConfigurationDto,
  InstanceDto,
  InstanceNodeConfigurationDto,
  MinionDto,
} from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { ErrorMessage, LoggingService } from 'src/app/modules/core/services/logging.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { mapObjToArray } from 'src/app/modules/core/utils/object.utils';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { GroupsService } from '../../groups/services/groups.service';
import { InstancesService } from './instances.service';

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
  apply(current: InstanceConfigurationDto);
}

/**
 * Creates a new edit, capturing delta between base and state.
 *
 * Note that a 'virtual' edit may be created without looking at base/state. Any edit will be
 * applied after it has been created.
 */
export type EditFactory = (description: string, base: InstanceConfigurationDto, state: InstanceConfigurationDto) => Edit;

/** A generic edit, which may represent virtually any change. */
export class InstanceEdit implements Edit {
  private changeset: Changeset;

  constructor(public description: string, base: InstanceConfigurationDto, current: InstanceConfigurationDto) {
    // clone changeset to decouple from changes to the source object.
    this.changeset = cloneDeep(diff(base, current));
  }

  public apply(current: InstanceConfigurationDto) {
    // clone changeset to decouple from changes through applying *another* change.
    applyChangeset(current, cloneDeep(this.changeset));
  }
}

/**
 * A special edit which move san application in the applications list.
 *
 * This is required to avoid *huge* diffs (add/remove) between applications. The huge diffs have the
 * *huge* drawback that null/undefined handling is extremely diffficult.
 */
export class InstanceApplicationMoveEdit implements Edit {
  constructor(public description: string, private nodeName: string, private previousIndex: number, private currentIndex: number) {}

  public apply(current: InstanceConfigurationDto) {
    moveItemInArray(current.nodeDtos.find((n) => n.nodeName === this.nodeName).nodeConfiguration.applications, this.previousIndex, this.currentIndex);
  }
}

@Injectable({
  providedIn: 'root',
})
export class InstanceEditService {
  private log = this.logging.getLogger('InstanceEditService');

  public loading$ = new BehaviorSubject<boolean>(true);
  public saving$ = new BehaviorSubject<boolean>(false);

  public incompatible$ = new BehaviorSubject<boolean>(false);

  public undos: Edit[] = [];
  public redos: Edit[] = [];
  public base$ = new BehaviorSubject<InstanceConfigurationDto>(null);

  public undo$ = new BehaviorSubject<Edit>(null);
  public redo$ = new BehaviorSubject<Edit>(null);

  public state$ = new BehaviorSubject<InstanceConfigurationDto>(null);
  public nodes$ = new BehaviorSubject<{ [key: string]: MinionDto }>(null);
  public baseApplications$ = new BehaviorSubject<ApplicationDto[]>(null);
  public stateApplications$ = new BehaviorSubject<ApplicationDto[]>(null);

  public current$ = new BehaviorSubject<InstanceDto>(null);

  private apiPath = (g) => `${this.cfg.config.api}/group/${g}/instance`;

  constructor(
    private http: HttpClient,
    private cfg: ConfigService,
    private logging: LoggingService,
    private groups: GroupsService,
    private instances: InstancesService,
    private areas: NavAreasService
  ) {
    this.instances.current$.subscribe((instance) => {
      // ALWAYS update current$ to pick up updated global data (banner, etc.).
      const cur = this.current$.value;
      this.current$.next(instance);

      if (instance?.instance?.name !== cur?.instance?.name || instance?.instance?.tag !== cur?.instance?.tag) {
        if (!!this.undos?.length || !!this.redos?.length) {
          // instance (version?) changes, but we have edits... this means we're basically doomed.
          this.log.warn('Instance update with pending changes.');
          this.incompatible$.next(true);
        } else {
          // either nothing loaded yet, or we do not have any changes, in this case we can reset as well.
          this.reset();
        }
      }
    });

    this.areas.panelRoute$.subscribe((route) => {
      if (this.isAllowEdit() && this.hasPendingChanges()) {
        this.log.warn('Unconcealed changes on route change, discarding.');
        this.discard();
      }
    });
  }

  /** Creates a standard edit factory, capturing changes by diffing. */
  public createStdEdit(): EditFactory {
    return (desc: string, base: InstanceConfigurationDto, state: InstanceConfigurationDto) => {
      return new InstanceEdit(desc, base, state);
    };
  }

  /** Creates a special edit factory which reorders applications on the given node */
  public createApplicationMove(node: string, previous: number, current: number): EditFactory {
    return (desc: string, base: InstanceConfigurationDto, state: InstanceConfigurationDto) => {
      return new InstanceApplicationMoveEdit(desc, node, previous, current);
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
      this.log.error(new ErrorMessage('Trying to edit with no current state', new Error()));
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

          this.base$.next(base);
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
    return this.hasChanges(concealed, this.state$.value);
  }

  /**
   * Determines whether there are concealed changes which can be saved to the server.
   *
   * @returns  whether there is one or more concealed changes.
   */
  public hasSaveableChanges(): boolean {
    return this.undos.length > 0;
  }

  /** Applies all current InstanceEdits and saves the result to the server. */
  public save() {
    if (!this.isAllowEdit()) {
      this.log.error(new ErrorMessage('Trying to save with no state', new Error()));
      return;
    }

    this.saving$.next(true);

    const state = this.reBuild();

    const managedServer = this.current$.value.managedServer?.hostName;
    const expect = this.current$.value.instance.tag;

    return this.http
      .post(`${this.apiPath(this.groups.current$.value.name)}/${state.config.uuid}`, state, { params: { managedServer, expect } })
      .pipe(
        finalize(() => this.saving$.next(false)),
        measure('Save Instance')
      )
      .subscribe((_) => {
        // success :) lets reset.
        this.reset();
      });
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
      },
    };
  }

  public getProcessEditState(uid: string): ProcessEditState {
    // process UID must be unique across nodes, so we check all nodes for the process.
    const baseNodes = this.base$.value?.nodeDtos;
    const stateNodes = this.state$.value?.nodeDtos;

    if (!baseNodes || !stateNodes) {
      return ProcessEditState.NONE;
    }

    const baseNode = this.getNodeOfApplication(baseNodes, uid);
    const stateNode = this.getNodeOfApplication(stateNodes, uid);

    const baseNodeApps = baseNode?.nodeConfiguration?.applications;
    const stateNodeApps = stateNode?.nodeConfiguration?.applications;

    const baseAppIndex = baseNodeApps?.findIndex((a) => a.uid === uid);
    const stateAppIndex = stateNodeApps?.findIndex((a) => a.uid === uid);

    // if undefined, we just switch to "not found" - the node might not even exist.
    const baseComp = baseAppIndex === undefined || baseAppIndex === null ? -1 : baseAppIndex;
    const stateComp = stateAppIndex === undefined || stateAppIndex === null ? -1 : stateAppIndex;

    // TODO: Validation state, must override ADDED/CHANGED
    if (baseComp === -1 && stateComp === -1) {
      return ProcessEditState.NONE;
    } else if (baseComp !== -1 && stateComp === -1) {
      return ProcessEditState.NONE; // removed means it's no longer there - it has no state.
    } else if (baseComp === -1 && stateComp !== -1) {
      return ProcessEditState.ADDED;
    } else {
      // both are !== -1
      if (
        baseAppIndex === stateAppIndex &&
        baseNode.nodeName === stateNode.nodeName &&
        !this.hasChanges(baseNodeApps[baseAppIndex], stateNodeApps[stateAppIndex])
      ) {
        return ProcessEditState.NONE;
      } else {
        return ProcessEditState.CHANGED;
      }
    }
  }

  private getNodeOfApplication(nodes: InstanceNodeConfigurationDto[], uid: string): InstanceNodeConfigurationDto {
    for (const node of nodes) {
      const app = node.nodeConfiguration.applications.find((a) => a.uid === uid);
      if (!!app) {
        return node;
      }
    }
    return null;
  }

  /** Creates the current state from the base and all recorded edits. */
  private reBuild(): InstanceConfigurationDto {
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
    return !isMatchWith(object, original, (value, other, key) => {
      if (isNil(value) && isNil(other)) {
        return true; // same regardless of "null" vs. "undefined".
      }
    });
  }
}
