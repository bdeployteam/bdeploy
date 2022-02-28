import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { cloneDeep, isEqual } from 'lodash-es';
import {
  BehaviorSubject,
  combineLatest,
  Observable,
  of,
  Subscription,
} from 'rxjs';
import { debounceTime, finalize, first, map, skipWhile } from 'rxjs/operators';
import {
  CustomAttributesRecord,
  HistoryFilterDto,
  HistoryResultDto,
  InstanceBannerRecord,
  InstanceConfiguration,
  InstanceDto,
  InstanceNodeConfigurationListDto,
  MinionStatusDto,
  ObjectChangeDetails,
  ObjectChangeHint,
  ObjectChangeType,
  RemoteDirectory,
  RemoteDirectoryEntry,
  StringEntryChunkDto,
} from 'src/app/models/gen.dtos';
import { DownloadService } from 'src/app/modules/core/services/download.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { NO_LOADING_BAR_CONTEXT } from 'src/app/modules/core/utils/loading-bar.util';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { ConfigService } from '../../../core/services/config.service';
import { ObjectChangesService } from '../../../core/services/object-changes.service';
import { GroupsService } from '../../groups/services/groups.service';
import { ProductsService } from '../../products/services/products.service';
import { ServersService } from '../../servers/services/servers.service';

@Injectable({
  providedIn: 'root',
})
export class InstancesService {
  listLoading$ = new BehaviorSubject<boolean>(true);
  activeLoading$ = new BehaviorSubject<boolean>(false);
  loading$ = combineLatest([this.listLoading$, this.activeLoading$]).pipe(
    map(([a, b]) => a || b)
  );

  instances$ = new BehaviorSubject<InstanceDto[]>([]);
  othersNeedReload$ = new BehaviorSubject<boolean>(false);

  /** the current instance version */
  current$ = new BehaviorSubject<InstanceDto>(null);

  /** the *active* instance version */
  active$ = new BehaviorSubject<InstanceDto>(null);
  activeNodeCfgs$ = new BehaviorSubject<InstanceNodeConfigurationListDto>(null);
  activeNodeStates$ = new BehaviorSubject<{ [key: string]: MinionStatusDto }>(
    null
  );
  /** the history for the *active* instance. this may not be fully complete history, it is meant for a brief overview of events on the instance. */
  activeHistory$ = new BehaviorSubject<HistoryResultDto>(null);
  private activeLoadInterval;
  private activeCheckInterval;

  private group: string;
  private subscription: Subscription;
  private update$ = new BehaviorSubject<string>(null);
  public importURL$ = new BehaviorSubject<string>(null);

  private apiPath = (g) => `${this.cfg.config.api}/group/${g}/instance`;

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    private changes: ObjectChangesService,
    private areas: NavAreasService,
    private serversService: ServersService,
    private downloads: DownloadService,
    private products: ProductsService,
    groups: GroupsService
  ) {
    combineLatest([groups.current$, products.products$]).subscribe(([group]) =>
      this.update$.next(group?.name)
    );
    areas.instanceContext$.subscribe((i) => this.loadCurrentAndActive(i));
    this.update$.pipe(debounceTime(100)).subscribe((g) => this.reload(g));

    combineLatest([
      this.current$,
      this.active$,
      this.serversService.servers$,
    ]).subscribe(([cur, act, servers]) => {
      this.importURL$.next(
        `${this.apiPath(this.group)}/${cur?.instanceConfiguration?.uuid}/import`
      );
      clearInterval(this.activeLoadInterval);
      clearInterval(this.activeCheckInterval);

      let update: Observable<InstanceDto[]> = of(null);

      if (this.othersNeedReload$.value) {
        this.othersNeedReload$.next(false);
        this.instances$.next(null);
        update = this.instances$.pipe(
          skipWhile((x) => !x),
          first()
        );
        this.reload(this.group);
      }

      update.subscribe((_) => {
        console.log(
          `Instance Change, Current=${cur?.instanceConfiguration?.uuid}:${cur?.instance?.tag}, Cur-Active=${cur?.activeVersion?.tag}, Active=${act?.instanceConfiguration?.uuid}:${act?.instance?.tag}`
        );

        // we'll refresh node states every 10 seconds as long as nothing else causes a reload. this
        // is a relatively cheap call nowadays, as this will simply fetch cached state from the node manager.
        this.activeLoadInterval = setInterval(
          () => this.reloadActiveStates(act),
          10000
        );
        this.activeCheckInterval = setInterval(
          () => this.checkActiveReloadState(act),
          1000
        );

        // update in case the server has changed (e.g. synchronized update state).
        if (!!servers?.length && !!cur?.managedServer?.hostName) {
          const s = servers.find(
            (s) => cur.managedServer.hostName === s.hostName
          );
          if (!!s && !isEqual(cur.managedServer, s)) {
            cur.managedServer = s;
            this.current$.next(cur);
          }
        }

        if (!!servers?.length && !!act?.managedServer?.hostName) {
          const c = servers.find(
            (x) => act.managedServer.hostName === x.hostName
          );
          if (!!c && !isEqual(act.managedServer, c)) {
            act.managedServer = c;
            this.active$.next(act);
          }
        }

        this.reloadActiveStates(act);
      });
    });
  }

  public create(
    instance: Partial<InstanceConfiguration>,
    managedServer: string
  ): Observable<any> {
    return this.http.put(`${this.apiPath(this.group)}`, instance, {
      params: { managedServer },
    });
  }

  public delete(instance: string): Observable<any> {
    this.current$.next(null);
    this.activeNodeCfgs$.next(null);
    this.active$.next(null);
    this.checkActiveReloadState(null);
    return this.http.delete(`${this.apiPath(this.group)}/${instance}/delete`);
  }

  public deleteVersion(version: string) {
    return this.http.delete(
      `${this.apiPath(this.group)}/${
        this.current$.value.instanceConfiguration.uuid
      }/deleteVersion/${version}`
    );
  }

  public updateAttributes(
    instance: string,
    attributes: CustomAttributesRecord
  ) {
    return this.http.post(
      `${this.apiPath(this.group)}/${instance}/attributes`,
      attributes
    );
  }

  public download(dir: RemoteDirectory, entry: RemoteDirectoryEntry) {
    const origin = this.current$.value;

    if (!origin) {
      return;
    }

    this.http
      .post(
        `${this.apiPath(this.group)}/${
          origin.instanceConfiguration.uuid
        }/request/${dir.minion}`,
        entry,
        { responseType: 'text' }
      )
      .subscribe((token) => {
        this.downloads.download(
          `${this.apiPath(this.group)}/${
            origin.instanceConfiguration.uuid
          }/stream/${token}`
        );
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
      return;
    }

    return this.http.post<StringEntryChunkDto>(
      `${this.apiPath(this.group)}/${
        origin.instanceConfiguration.uuid
      }/content/${dir.minion}`,
      entry,
      {
        params: { offset: offset.toString(), length: length.toString() },
        context: NO_LOADING_BAR_CONTEXT,
      }
    );
  }

  public loadHistory(
    filter: Partial<HistoryFilterDto>
  ): Observable<HistoryResultDto> {
    return this.http
      .post<HistoryResultDto>(
        `${this.apiPath(this.group)}/${
          this.current$.value.instanceConfiguration.uuid
        }/history`,
        filter
      )
      .pipe(measure('Current Instance History'));
  }

  public loadNodes(
    instance: string,
    tag: string
  ): Observable<InstanceNodeConfigurationListDto> {
    return this.http.get<InstanceNodeConfigurationListDto>(
      `${this.apiPath(this.group)}/${instance}/${tag}/nodeConfiguration`
    );
  }

  public updateBanner(banner: InstanceBannerRecord): Observable<any> {
    return this.http
      .post(
        `${this.apiPath(this.group)}/${
          this.current$.value.instanceConfiguration.uuid
        }/banner`,
        banner
      )
      .pipe(measure('Update Instance Banner'));
  }

  public export(tag: string) {
    const url = `${this.apiPath(this.group)}/${
      this.current$.value.instanceConfiguration.uuid
    }/export/${tag}`;
    this.downloads.download(url);
  }

  private reload(group: string) {
    if (!group) {
      this.instances$.next([]);
      this.loadCurrentAndActive(null);
      this.updateChangeSubscription(null);
      return;
    }

    if (this.group !== group || !this.subscription) {
      this.updateChangeSubscription(group);
    }

    this.group = group;
    this.listLoading$.next(true);
    this.http
      .get<InstanceDto[]>(`${this.apiPath(group)}`)
      .pipe(
        finalize(() => this.listLoading$.next(false)),
        measure('Instance Load')
      )
      .subscribe((instances) => {
        this.instances$.next(instances);

        // last update the current$ subject to inform about changes
        if (this.areas.instanceContext$.value) {
          this.loadCurrentAndActive(this.areas.instanceContext$.value);
        }
      });
  }

  private updateChangeSubscription(group: string) {
    if (this.subscription) {
      this.subscription.unsubscribe();
      this.subscription = null;
    }

    if (group) {
      this.subscription = this.changes.subscribe(
        ObjectChangeType.INSTANCE,
        { scope: [group] },
        (change) => {
          if (
            !!this.current$.value?.instanceConfiguration?.uuid &&
            !change.scope.scope.includes(
              this.current$.value.instanceConfiguration.uuid
            )
          ) {
            this.othersNeedReload$.next(true); // when switching to another instance, we need to reload all.
            return;
          }

          this.update$.next(group);
          if (change.details[ObjectChangeDetails.CHANGE_HINT]) {
            if (
              change.details[ObjectChangeDetails.CHANGE_HINT] ===
                ObjectChangeHint.BANNER &&
              !!this.active$.value
            ) {
              // update banner in active version if it changes on the server.
              this.http
                .get<InstanceBannerRecord>(
                  `${this.apiPath(this.group)}/${
                    this.active$.value.instanceConfiguration.uuid
                  }/banner`
                )
                .subscribe((banner) => {
                  this.active$.value.banner = banner;
                  this.active$.next(this.active$.value);
                });
            }
          }
        }
      );
    }
  }

  private loadCurrentAndActive(i: string) {
    const inst = this.instances$.value?.find(
      (x) => x.instanceConfiguration.uuid === i
    );

    // we can always set the *current* version.
    this.current$.next(inst);

    // only load nodes in case there is an active version.
    if (!inst || !inst.activeVersion) {
      this.checkActiveReloadState(inst);

      // not yet loaded, instance not existant, etc.
      this.activeNodeCfgs$.next(null);
      this.active$.next(null);
      return;
    }

    console.log(
      `Load Instance, Current=${inst.instanceConfiguration.uuid}:${inst.instance.tag}, Active=${inst.activeVersion.tag}, Old-Active=${this.active$.value?.instanceConfiguration?.uuid}:${this.active$.value?.instance?.tag}`
    );

    if (
      i === this.active$.value?.instanceConfiguration?.uuid &&
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
          `${this.apiPath(this.group)}/${inst.instanceConfiguration.uuid}/${
            inst.activeVersion.tag
          }`
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
      this.http
        .get<InstanceNodeConfigurationListDto>(
          `${this.apiPath(this.group)}/${act.instanceConfiguration.uuid}/${
            act.activeVersion.tag
          }/nodeConfiguration`
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

    this.http
      .get<{ [minionName: string]: MinionStatusDto }>(
        `${this.apiPath(this.group)}/${act.instanceConfiguration.uuid}/${
          act.activeVersion.tag
        }/minionState`
      )
      .pipe(measure('Node States'))
      .subscribe((states) => {
        this.activeNodeStates$.next(states);
      });

    const historyFilter: Partial<HistoryFilterDto> = {
      showCreateEvents: true,
      showDeploymentEvents: true,
      showRuntimeEvents: true,
      maxResults: 150,
    };
    this.http
      .post<HistoryResultDto>(
        `${this.apiPath(this.group)}/${act.instanceConfiguration.uuid}/history`,
        historyFilter
      )
      .pipe(measure('Active Instance History'))
      .subscribe((history) => {
        this.activeHistory$.next(history);
      });
  }
}
