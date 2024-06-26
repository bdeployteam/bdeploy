import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, Subscription, of } from 'rxjs';
import { debounceTime, finalize, tap } from 'rxjs/operators';
import {
  ManagedMasterDto,
  MinionSyncResultDto,
  ObjectChangeType,
  ProductDto,
  ProductTransferDto,
} from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { ObjectChangesService } from 'src/app/modules/core/services/object-changes.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { suppressGlobalErrorHandling } from 'src/app/modules/core/utils/server.utils';
import { GroupsService } from '../../groups/services/groups.service';

const SYNC_TIMEOUT = 1000 * 60 * 15;

export enum AttachType {
  AUTO = 'AUTO',
  MANUAL = 'MANUAL',
}

@Injectable({
  providedIn: 'root',
})
export class ServersService {
  private cfg = inject(ConfigService);
  private http = inject(HttpClient);
  private changes = inject(ObjectChangesService);
  private groups = inject(GroupsService);

  public loading$ = new BehaviorSubject<boolean>(true);
  public servers$ = new BehaviorSubject<ManagedMasterDto[]>([]);

  public isCurrentInstanceSynchronized$ = new BehaviorSubject<boolean>(true);
  public isServerDetailsSynchronized$ = new BehaviorSubject<boolean>(true);

  private apiPath = `${this.cfg.config.api}/managed-servers`;
  private group: string;
  private subscription: Subscription;
  private isCentral = false;

  private update$ = new BehaviorSubject<string>(null);

  constructor() {
    this.groups.current$.subscribe((g) => this.update$.next(g?.name));
    this.update$.pipe(debounceTime(100)).subscribe((g) => this.reload(g));
    this.cfg.isCentral$.subscribe((value) => {
      this.isCentral = value;
    });
  }

  private updateChangeSubscription(group: string) {
    this.subscription?.unsubscribe();
    this.subscription = null;

    if (group) {
      this.subscription = this.changes.subscribe(ObjectChangeType.INSTANCE_GROUP, { scope: [group] }, () => {
        this.update$.next(group);
      });
    }
  }

  private reload(group: string) {
    if (!group || !this.isCentral) {
      this.group = null;
      this.servers$.next([]);
      this.updateChangeSubscription(null);
      return;
    }

    if (group !== this.group) {
      this.updateChangeSubscription(group);
    }

    this.group = group;
    this.loading$.next(true);
    this.http
      .get<ManagedMasterDto[]>(`${this.apiPath}/list/${group}`)
      .pipe(
        finalize(() => this.loading$.next(false)),
        measure('Managed Server Load'),
      )
      .subscribe((s) => {
        this.servers$.next(s);
      });
  }

  public synchronize(server: ManagedMasterDto): Observable<MinionSyncResultDto> {
    if (this.isCentral) {
      return this.http.get<MinionSyncResultDto>(`${this.apiPath}/synchronize/${this.group}/${server.hostName}`).pipe(
        tap((s) => {
          if (this.servers$.value?.length) {
            this.servers$.value.splice(
              this.servers$.value.findIndex((o) => o.hostName === s.server.hostName),
              1,
              s.server,
            );

            this.servers$.next(this.servers$.value);
          }
        }),
      );
    }
    return of(null);
  }

  public isSynchronized(server: ManagedMasterDto): boolean {
    if (this.isCentral && !!server) {
      return this.getSynchronizedOffset(server) <= SYNC_TIMEOUT;
    }
    return true;
  }

  public updateInstanceSyncState(server: ManagedMasterDto) {
    this.isCurrentInstanceSynchronized$.next(this.isSynchronized(server));
  }

  public updateServerSyncState(server: ManagedMasterDto) {
    this.isServerDetailsSynchronized$.next(this.isSynchronized(server));
  }

  public getRemainingSynchronizedTime(server: ManagedMasterDto): number {
    return SYNC_TIMEOUT - this.getSynchronizedOffset(server);
  }

  private getSynchronizedOffset(server: ManagedMasterDto): number {
    if (this.isCentral) {
      // prefer current information if loaded.
      const currentS = this.servers$.value?.find((s) => s.hostName === server.hostName);
      const currentTime = this.cfg.getCorrectedNow(); // use server time to compare.
      return currentTime - (currentS || server).lastSync;
    }
    return 0;
  }

  public attachManaged(server: ManagedMasterDto): Observable<AttachType> {
    return new Observable((s) => {
      this.http
        .put(`${this.apiPath}/auto-attach/${this.group}`, server, {
          headers: suppressGlobalErrorHandling(new HttpHeaders()),
        })
        .subscribe({
          next: () => {
            s.next(AttachType.AUTO);
            s.complete();
          },
          error: () => {
            this.http.put(`${this.apiPath}/manual-attach/${this.group}`, server).subscribe({
              next: () => {
                s.next(AttachType.MANUAL);
                s.complete();
              },
              error: (manualErr) => {
                s.error(manualErr);
                s.complete();
              },
            });
          },
        });
    });
  }

  public manualAttachCentral(ident: string): Observable<string> {
    return this.http.put(`${this.apiPath}/manual-attach-central`, ident, {
      responseType: 'text',
    });
  }

  public getCentralIdent(server: ManagedMasterDto): Observable<string> {
    return this.http.post(`${this.apiPath}/central-ident/${this.group}`, server, { responseType: 'text' });
  }

  public getManagedIdent(): Observable<ManagedMasterDto> {
    // TODO: why is this method in the wrong service on the server?
    return this.http.get<ManagedMasterDto>(`${this.cfg.config.api}/backend-info/managed-master`);
  }

  public getRemoteProducts(server: string): Observable<ProductDto[]> {
    return this.http
      .get<ProductDto[]>(`${this.apiPath}/list-products/${this.group}/${server}`)
      .pipe(measure('Load remote products'));
  }

  public transferProducts(transfer: ProductTransferDto): Observable<unknown> {
    return this.http
      .post(`${this.apiPath}/transfer-products/${this.group}`, transfer)
      .pipe(measure('Initiate Product Transfer'));
  }
}
