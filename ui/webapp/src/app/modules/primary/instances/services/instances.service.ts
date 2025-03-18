import { HttpClient } from '@angular/common/http';
import { inject, Injectable, NgZone } from '@angular/core';
import { cloneDeep, isEqual } from 'lodash-es';
import {
  BehaviorSubject,
  combineLatest,
  forkJoin,
  Observable,
  of,
  share,
  ShareConfig,
  Subscription,
  switchMap
} from 'rxjs';
import { debounceTime, finalize, first, map, skip, skipWhile, tap } from 'rxjs/operators';
import {
  CustomAttributesRecord,
  HistoryFilterDto,
  HistoryResultDto,
  InstanceBannerRecord,
  InstanceConfiguration,
  InstanceDto,
  InstanceNodeConfigurationListDto,
  InstanceOverallStatusDto,
  ManifestKey,
  MinionStatusDto,
  ObjectChangeDetails,
  ObjectChangeDto,
  ObjectChangeHint,
  ObjectChangeType,
  ObjectEvent,
  ProductUpdateDto,
  RemoteDirectory,
  RemoteDirectoryEntry,
  StringEntryChunkDto
} from 'src/app/models/gen.dtos';
import { DownloadService } from 'src/app/modules/core/services/download.service';
import { HttpReplayService } from 'src/app/modules/core/services/http-replay.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { NO_LOADING_BAR_CONTEXT } from 'src/app/modules/core/utils/loading-bar.util';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { ConfigService } from '../../../core/services/config.service';
import { ObjectChangesService } from '../../../core/services/object-changes.service';
import { GroupsService } from '../../groups/services/groups.service';
import { ProductsService } from '../../products/services/products.service';
import { ServersService } from '../../servers/services/servers.service';

const DEF_PROD_UPDATES: ProductUpdateDto = {
  newerVersionAvailable: false,
  newerVersionAvailableInRepository: null
};

const DEF_SHARE_CONFIG: ShareConfig<ProductUpdateDto> = {
  connector: () => new BehaviorSubject(DEF_PROD_UPDATES),
  resetOnRefCountZero: false
};

@Injectable({
  providedIn: 'root'
})
export class InstancesService {
  private readonly cfg = inject(ConfigService);
  private readonly http = inject(HttpClient);
  private readonly changes = inject(ObjectChangesService);
  private readonly areas = inject(NavAreasService);
  private readonly serversService = inject(ServersService);
  private readonly downloads = inject(DownloadService);
  private readonly httpReplayService = inject(HttpReplayService);
  private readonly products = inject(ProductsService);
  private readonly groups = inject(GroupsService);
  private readonly ngZone = inject(NgZone);

  private readonly apiPath = (g: string) => `${this.cfg.config.api}/group/${g}/instance`;

  public listLoading$ = new BehaviorSubject<boolean>(true);
  public activeLoading$ = new BehaviorSubject<boolean>(false);
  public loading$ = combineLatest([this.listLoading$, this.activeLoading$]).pipe(map(([a, b]) => a || b));

  public instances$ = new BehaviorSubject<InstanceDto[]>([]);
  public instancesChanges: ObjectChangeDto[] = [];

  /** the current instance version */
  public current$ = new BehaviorSubject<InstanceDto>(null);
  public productUpdates$ = this.current$.pipe(
    map((i) => i?.instanceConfiguration?.id),
    switchMap((i) => (i ? this.loadProductUpdates(i).pipe(map((u) => u || DEF_PROD_UPDATES)) : of(DEF_PROD_UPDATES))),
    share(DEF_SHARE_CONFIG)
  );

  /** the *active* instance version */
  public active$ = new BehaviorSubject<InstanceDto>(null);
  public activeNodeCfgs$ = new BehaviorSubject<InstanceNodeConfigurationListDto>(null);
  public activeNodeStates$ = new BehaviorSubject<Record<string, MinionStatusDto>>(null);
  /** the history for the *active* instance. this may not be fully complete history, it is meant for a brief overview of events on the instance. */
  public activeHistory$ = new BehaviorSubject<HistoryResultDto>(null);

  private activeStateCall: Subscription;
  private activeHistoryCall: Subscription;

  private activeLoadInterval: ReturnType<typeof setInterval>;
  private activeCheckInterval: ReturnType<typeof setInterval>;

  public overallStates$ = new BehaviorSubject<InstanceOverallStatusDto[]>([]);
  public overallStatesLoading$ = new BehaviorSubject<boolean>(false);

  private group: string;
  private subscription: Subscription;
  private readonly update$ = new BehaviorSubject<string>(null);
  public importURL$ = new BehaviorSubject<string>(null);

  constructor() {
    // clear out stuff whenever the group is re-set.
    this.groups.current$.subscribe(() => {
      // whenever the current group changes, we trigger a delayed reload (below).
      // we *anyhow* want to remove the outdated data before doing this.
      // otherwise the user would briefly see the old data before loading begins.
      this.listLoading$.next(true); // will load in a bit.
      this.instances$.next([]);
    });

    // reload instances if products changed for central
    // otherwise we might see N/A chip next to product version even after product has been downloaded to central
    combineLatest([this.products.products$, this.cfg.isCentral$])
      .pipe(skipWhile(([p, c]) => !p || !c))
      .subscribe(() => {
        this.listLoading$.next(true); // will be reloaded in combineLatest(groups.current$, products.products$)
        this.instances$.next([]);
      });

    // only do actual loading once BOTH group and products are ready.
    combineLatest([this.groups.current$, this.products.products$]).subscribe(([group, products]) => {
      if (group && products) {
        this.update$.next(group.name);
      }
    });
    this.areas.instanceContext$.subscribe((i) => this.loadCurrentAndActive(i));
    this.update$.pipe(debounceTime(100)).subscribe((g) => this.reload(g));

    this.active$.subscribe((act) => {
      clearInterval(this.activeLoadInterval);
      clearInterval(this.activeCheckInterval);
      // we'll refresh node states every 10 seconds as long as nothing else causes a reload. this
      // is a relatively cheap call nowadays, as this will simply fetch cached state from the node manager.
      this.ngZone.runOutsideAngular(() => {
        this.activeLoadInterval = setInterval(() => this.reloadActiveStates(act), 10000);
        this.activeCheckInterval = setInterval(() => this.checkActiveReloadState(act), 1000);
      });
      this.reloadActiveStates(act);
    });

    combineLatest([this.current$, this.active$, this.serversService.servers$]).subscribe(([cur, act, servers]) => {
      this.importURL$.next(`${this.apiPath(this.group)}/${cur?.instanceConfiguration?.id}/import`);

      let update: Observable<InstanceDto[]> = of(null);
      if (this.instancesChanges.length > 0) {
        update = this.instances$.pipe(skip(1), first()); // wait for the next reloaded value
        this.reload(this.group);
      }

      update.subscribe(() => {
        // update in case the server has changed (e.g. synchronized update state).
        if (!!servers?.length && !!cur?.managedServer?.hostName) {
          const s = servers.find((managedMasterDto) => cur.managedServer.hostName === managedMasterDto.hostName);
          if (!!s && !isEqual(cur.managedServer, s)) {
            cur.managedServer = s;
            this.current$.next(cur);

            // if current == active, we need to trigger active update as well!
            // if it is equal the below if will not detect a change for active.
            if (cur === act) {
              this.active$.next(act);
            }
          }
        }

        if (!!servers?.length && !!act?.managedServer?.hostName) {
          const c = servers.find((x) => act.managedServer.hostName === x.hostName);
          if (!!c && !isEqual(act.managedServer, c)) {
            act.managedServer = c;
            this.active$.next(act);
          }
        }
      });
    });
  }

  public create(instance: Partial<InstanceConfiguration>, managedServer: string): Observable<any> {
    return this.http.put(`${this.apiPath(this.group)}`, instance, {
      params: { managedServer }
    });
  }

  public delete(instance: string): Observable<unknown> {
    return this.http.delete(`${this.apiPath(this.group)}/${instance}/delete`).pipe(
      tap(() => {
        this.current$.next(null);
        this.activeNodeCfgs$.next(null);
        this.active$.next(null);
        this.checkActiveReloadState(null);
      })
    );
  }

  public deleteVersion(version: string) {
    return this.http.delete(
      `${this.apiPath(this.group)}/${this.current$.value.instanceConfiguration.id}/deleteVersion/${version}`
    );
  }

  public updateAttributes(instance: string, attributes: CustomAttributesRecord) {
    return this.http.post(`${this.apiPath(this.group)}/${instance}/attributes`, attributes);
  }

  public download(dir: RemoteDirectory, entry: RemoteDirectoryEntry) {
    const origin = this.current$.value;

    if (!origin) {
      return;
    }

    this.http
      .post(`${this.apiPath(this.group)}/${origin.instanceConfiguration.id}/request/${dir.minion}`, entry, {
        responseType: 'text'
      })
      .subscribe((token) => {
        this.downloads.download(`${this.apiPath(this.group)}/${origin.instanceConfiguration.id}/stream/${token}`);
      });
  }

  public getContentChunk(
    dir: RemoteDirectory,
    entry: RemoteDirectoryEntry,
    offset: number,
    length: number
  ): Observable<StringEntryChunkDto> {
    const origin = this.current$.value;

    if (!origin) {
      return null;
    }

    return this.http.post<StringEntryChunkDto>(
      `${this.apiPath(this.group)}/${origin.instanceConfiguration.id}/content/${dir.minion}`,
      entry,
      {
        params: { offset: offset.toString(), length: length.toString() },
        context: NO_LOADING_BAR_CONTEXT
      }
    );
  }

  public loadHistory(filterDto: Partial<HistoryFilterDto>): Observable<HistoryResultDto> {
    return this.http
      .post<HistoryResultDto>(
        `${this.apiPath(this.group)}/${this.current$.value.instanceConfiguration.id}/history`,
        filterDto
      )
      .pipe(measure('Current Instance History'));
  }

  public loadNodes(instance: string, tag: string): Observable<InstanceNodeConfigurationListDto> {
    return this.httpReplayService.get<InstanceNodeConfigurationListDto>(
      `${this.apiPath(this.group)}/${instance}/${tag}/nodeConfiguration`
    );
  }

  public loadProductUpdates(instance: string): Observable<ProductUpdateDto> {
    return this.httpReplayService
      .get<ProductUpdateDto>(`${this.apiPath(this.group)}/${instance}/update-check`)
      .pipe(measure('Check for product updates'));
  }

  public updateBanner(banner: InstanceBannerRecord): Observable<unknown> {
    return this.http
      .post(`${this.apiPath(this.group)}/${this.current$.value.instanceConfiguration.id}/banner`, banner)
      .pipe(measure('Update Instance Banner'));
  }

  public export(tag: string) {
    const url = `${this.apiPath(this.group)}/${this.current$.value.instanceConfiguration.id}/export/${tag}`;
    this.downloads.download(url);
  }

  private reload(group: string) {
    if (!group) {
      this.group = null;
      this.instances$.next([]);
      this.loadCurrentAndActive(null);
      this.updateChangeSubscription(null);
      return;
    }

    if (this.group !== group || !this.subscription) {
      this.updateChangeSubscription(group);
    }

    this.group = group;

    this.getInstances(group).subscribe((instances) => {
      this.instances$.next(instances);
      this.overallStates$.next(
        instances.map((x) => ({
          id: x.instanceConfiguration.id,
          ...x.overallState
        }))
      );

      // last update the current$ subject to inform about changes
      if (this.areas.instanceContext$.value) {
        this.loadCurrentAndActive(this.areas.instanceContext$.value);
      }
    });
  }

  private getInstances(group: string): Observable<InstanceDto[]> {
    const fetchInstances$ = this.canPartiallyReloadInstances(group)
      ? this.partiallyReloadInstances(group, this.getDeletedInstanceIds(), this.getUpdatedInstanceIds())
      : this.reloadInstances(group);
    this.instancesChanges = [];
    this.listLoading$.next(true);
    return fetchInstances$.pipe(finalize(() => this.listLoading$.next(false)));
  }

  private canPartiallyReloadInstances(group: string): boolean {
    // if instances were reset or were not pulled yet we need to reload
    if (!this.instances$.value?.length) {
      return false;
    }

    const wrongScopeLength = this.instancesChanges.find((change) => change.scope.scope.length < 2);
    if (wrongScopeLength) {
      console.warn(
        `Found instance change with scope length ${wrongScopeLength.scope.scope.length} (less than 2). Reloading.`
      );
      return false;
    }

    const wrongGroup = this.instancesChanges.find((change) => change.scope.scope[0] !== group);
    if (wrongGroup) {
      console.warn(
        `Found instance change that belongs to group ${wrongGroup.scope.scope[0]} instead of ${group}. Reloading`
      );
      return false;
    }

    // if more than 3 instances were changed, then let's reload all
    if (this.getUpdatedInstanceIds().length > 3) {
      return false;
    }

    return true;
  }

  public reloadCurrentInstance() {
    // consider the current instance as changed and partially reload to update the list.
    this.partiallyReloadInstances(this.group, [], [this.current$.value?.instanceConfiguration?.id]).subscribe(
      (list) => {
        this.instances$.next(list);
      }
    );
  }

  private partiallyReloadInstances(
    group: string,
    deletedInstanceIds: string[],
    updatedInstanceIds: string[]
  ): Observable<InstanceDto[]> {
    // remove changed instances. updated/created will be reloaded, and deleted should be excluded
    const unchangedInstances = this.instances$.value.filter(
      (i) =>
        !deletedInstanceIds.includes(i.instanceConfiguration.id) &&
        !updatedInstanceIds.includes(i.instanceConfiguration.id)
    );

    // if no instances were created/updated, we can return immediately (since deleted instances were already removed)
    if (updatedInstanceIds.length === 0) {
      return of(unchangedInstances);
    }

    const fetchUpdated = updatedInstanceIds.map((id) => this.http.get<InstanceDto>(`${this.apiPath(group)}/${id}`));

    return forkJoin(fetchUpdated).pipe(
      map((updated) => {
        return [...unchangedInstances, ...updated].sort((a, b) =>
          a.instanceConfiguration.name.localeCompare(b.instanceConfiguration.name)
        );
      }),
      measure('Instance Partial Load')
    );
  }

  private reloadInstances(group: string): Observable<InstanceDto[]> {
    return this.http.get<InstanceDto[]>(`${this.apiPath(group)}`).pipe(measure('Instance Load'));
  }

  private getDeletedInstanceIds(): string[] {
    return this.instancesChanges
      .filter((change) => change.event === ObjectEvent.REMOVED)
      .filter((change) => !change.details['partial'])
      .map((change) => change.scope.scope[1]);
  }

  private getUpdatedInstanceIds(): string[] {
    const deletedInstanceIds = this.getDeletedInstanceIds();
    const updatedInstanceIds = this.instancesChanges
      .filter((change) => change.event !== ObjectEvent.REMOVED || !!change.details['partial']) // partial REMOVED is considered an update (only a version was removed)
      .map((change) => change.scope.scope[1])
      .filter((id) => !deletedInstanceIds.includes(id)); // in case instance was created/updated and later removed, we don't care about that
    return [...new Set(updatedInstanceIds)]; // removing duplicate ids
  }

  public syncAndFetchState(instances: ManifestKey[]): void {
    this.overallStatesLoading$.next(true);
    this.http
      .post<InstanceOverallStatusDto[]>(`${this.apiPath(this.group)}/bulk/bulkSync`, instances)
      .pipe(
        finalize(() => this.overallStatesLoading$.next(false)),
        measure('Sync and fetch instance state')
      )
      .subscribe((s) => {
        // merge the result according to id in the existing list.
        this.updateStatusDtos(s);
      });
  }

  public updateStatusDtos(s: InstanceOverallStatusDto[]) {
    const old = this.overallStates$.value || [];
    s.forEach((x) => {
      const i = old.findIndex((y) => y.id === x.id);
      if (i !== -1) {
        old.splice(i, 1, x);
      } else {
        old.push(x);
      }
    });
    this.overallStates$.next(old);
  }

  private updateChangeSubscription(group: string) {
    this.subscription?.unsubscribe();
    this.subscription = null;

    this.instancesChanges = []; // if group has changed, then we don't want to track changes for prev group's instances

    if (!group) {
      return;
    }

    this.subscription = this.changes.subscribe(ObjectChangeType.INSTANCE, { scope: [group] }, (change) => {
      this.instancesChanges.push(change);

      // if it is not the current instance that changed, we can defer reload
      if (
        !!this.current$.value?.instanceConfiguration?.id &&
        !change.scope.scope.includes(this.current$.value.instanceConfiguration.id)
      ) {
        return;
      }

      this.update$.next(group);
      if (change.details[ObjectChangeDetails.CHANGE_HINT] === ObjectChangeHint.BANNER && !!this.active$.value) {
        // update banner in active version if it changes on the server.
        this.http
          .get<InstanceBannerRecord>(
            `${this.apiPath(this.group)}/${this.active$.value.instanceConfiguration.id}/banner`
          )
          .subscribe((banner) => {
            this.active$.value.banner = banner;
            this.active$.next(this.active$.value);
          });
      }
    });
  }

  private loadCurrentAndActive(i: string) {
    const inst = this.instances$.value?.find((x) => x.instanceConfiguration.id === i);

    // we can always set the *current* version.
    this.current$.next(inst);

    // only load nodes in case there is an active version.
    if (!inst?.activeVersion) {
      this.checkActiveReloadState(inst);

      // not yet loaded, instance not existant, etc.
      this.activeNodeCfgs$.next(null);
      this.active$.next(null);
      return;
    }

    if (
      i === this.active$.value?.instanceConfiguration?.id &&
      inst.activeVersion.tag === this.active$.value?.instance?.tag
    ) {
      // we already have the active version loaded, nothing to do.
      return;
    }

    // we either take the instance of it *is* the active version, or need to load the active config.
    let activeFetch = of(inst);
    if (inst.activeVersion.tag !== inst.instance.tag) {
      activeFetch = this.http
        .get<InstanceConfiguration>(
          `${this.apiPath(this.group)}/${inst.instanceConfiguration.id}/${inst.activeVersion.tag}`
        )
        .pipe(
          measure('Active Instance Configuration Load'),
          map((c) => {
            // we "just" replace the configuration so we have all the other non-versioned information here as well for the dashboard and others.
            // TODO: might be cooler if reading a single instance would yield the same data format as listing all current instances.
            const r = cloneDeep(inst);
            r.instance = r.activeVersion;
            r.instanceConfiguration = c;
            return r;
          })
        );
    }

    // otherwise load nodes first, and *then* set the current instance.
    this.activeLoading$.next(true);
    activeFetch.subscribe((act) => {
      this.httpReplayService
        .get<InstanceNodeConfigurationListDto>(
          `${this.apiPath(this.group)}/${act.instanceConfiguration.id}/${act.activeVersion.tag}/nodeConfiguration`
        )
        .pipe(
          finalize(() => this.activeLoading$.next(false)),
          measure('Active Nodes Load')
        )
        .subscribe((nodes) => {
          this.activeNodeCfgs$.next(nodes);
          this.active$.next(act);
        });
    });
  }

  private checkActiveReloadState(act: InstanceDto): boolean {
    if (!act || !this.serversService.isSynchronized(act.managedServer)) {
      clearInterval(this.activeLoadInterval);
      clearInterval(this.activeCheckInterval);
      this.activeNodeStates$.next(null);
      this.activeHistory$.next(null);
      return false;
    }

    return true;
  }

  /** Reloads the currently active instance's node states and history */
  public reloadActiveStates(act: InstanceDto) {
    if (!this.checkActiveReloadState(act)) {
      return;
    }

    this.activeStateCall =
      this.activeStateCall ||
      this.http
        .get<Record<string, MinionStatusDto>>(
          `${this.apiPath(this.group)}/${act.instanceConfiguration.id}/${act.activeVersion.tag}/minionState`
        )
        .pipe(
          measure('Node States'),
          finalize(() => (this.activeStateCall = null))
        )
        .subscribe((states) => {
          this.activeNodeStates$.next(states);
        });

    const historyFilter: Partial<HistoryFilterDto> = {
      showCreateEvents: true,
      showDeploymentEvents: true,
      showRuntimeEvents: true,
      maxResults: 150
    };
    this.activeHistoryCall =
      this.activeHistoryCall ||
      this.http
        .post<HistoryResultDto>(`${this.apiPath(this.group)}/${act.instanceConfiguration.id}/history`, historyFilter)
        .pipe(
          measure('Active Instance History'),
          finalize(() => (this.activeHistoryCall = null))
        )
        .subscribe((history) => {
          this.activeHistory$.next(history);
        });
  }
}
