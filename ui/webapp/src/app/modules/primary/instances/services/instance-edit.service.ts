import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { applyChangeset, Changeset, diff } from 'json-diff-ts';
import { cloneDeep, isEqual } from 'lodash-es';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { ApplicationDescriptor, InstanceConfigurationDto, InstanceDto } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { ErrorMessage, LoggingService } from 'src/app/modules/core/services/logging.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { GroupsService } from '../../groups/services/groups.service';
import { InstancesService } from './instances.service';

export class InstanceEdit {
  private changeset: Changeset;

  constructor(public description: string, base: InstanceConfigurationDto, current: InstanceConfigurationDto) {
    this.changeset = diff(base, current);
  }

  public apply(current: InstanceConfigurationDto) {
    applyChangeset(current, this.changeset);
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

  public undos: InstanceEdit[] = [];
  public redos: InstanceEdit[] = [];
  public base$ = new BehaviorSubject<InstanceConfigurationDto>(null);

  public undo$ = new BehaviorSubject<InstanceEdit>(null);
  public redo$ = new BehaviorSubject<InstanceEdit>(null);

  public state$ = new BehaviorSubject<InstanceConfigurationDto>(null);
  public baseApplications$ = new BehaviorSubject<{ [key: string]: ApplicationDescriptor }>(null);
  public stateApplications$ = new BehaviorSubject<{ [key: string]: ApplicationDescriptor }>(null);

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

  /**
   * Conceals the current differences to the base state as a single InstanceEdit.
   *
   * @param description a textual description of the changes.
   */
  public conceal(description: string): void {
    if (!this.isAllowEdit()) {
      this.log.error(new ErrorMessage('Trying to edit with no current state', new Error()));
      return;
    }

    // clear redo, no longer valid.
    this.redos = [];
    this.redo$.next(null);

    // the current state
    const state = this.reBuild();

    // and the diff to it :)
    const change = new InstanceEdit(description, state, this.state$.value);
    this.undos.push(change);
    this.undo$.next(change);

    // re-publish the state to inform others.
    this.state$.next(this.state$.value);
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

    const inst = this.instances.current$.value;
    if (!!inst) {
      this.loading$.next(true);
      this.instances
        .loadNodes(inst.instanceConfiguration.uuid, inst.instance.tag)
        .pipe(finalize(() => this.loading$.next(false)))
        .subscribe((nodes) => {
          this.baseApplications$.next(nodes.applications);
          this.stateApplications$.next(nodes.applications);

          const base: InstanceConfigurationDto = { config: inst.instanceConfiguration, nodeDtos: nodes.nodeConfigDtos };

          this.base$.next(base);
          this.state$.next(cloneDeep(base));
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
    return !isEqual(concealed, this.state$.value);
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

  /** Creates the current state from the base and all recorded edits. */
  private reBuild(): InstanceConfigurationDto {
    if (!this.base$.value) {
      return null;
    }
    const state = cloneDeep(this.base$.value);
    this.undos.forEach((c) => c.apply(state));
    return state;
  }

  private isAllowEdit(): boolean {
    if (!this.current$.value || !this.base$.value) {
      return false;
    }

    return !this.incompatible$.value;
  }
}
