import { Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { isEqual } from 'lodash-es';
import { BehaviorSubject, Subscription, combineLatest, of } from 'rxjs';
import { catchError, finalize, map, skipWhile, switchMap } from 'rxjs/operators';
import { Actions, HistoryEntryDto, HistoryEntryType, InstanceStateRecord, ManifestKey } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { HistoryService } from 'src/app/modules/primary/instances/services/history.service';
import { InstanceStateService } from 'src/app/modules/primary/instances/services/instance-state.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { HistoryDetailsService } from '../../services/history-details.service';
import { histKey, histKeyDecode } from '../../utils/history-key.utils';

@Component({
  selector: 'app-history-entry',
  templateUrl: './history-entry.component.html',
})
export class HistoryEntryComponent implements OnInit, OnDestroy {
  private readonly areas = inject(NavAreasService);
  private readonly history = inject(HistoryService);
  private readonly details = inject(HistoryDetailsService);
  private readonly actions = inject(ActionsService);
  private readonly groups = inject(GroupsService);
  private readonly products = inject(ProductsService);
  protected readonly instances = inject(InstancesService);
  protected readonly states = inject(InstanceStateService);
  protected readonly servers = inject(ServersService);
  protected readonly auth = inject(AuthenticationService);

  protected entry$ = new BehaviorSubject<HistoryEntryDto>(null);
  protected state$ = new BehaviorSubject<InstanceStateRecord>(null);

  private readonly installing$ = new BehaviorSubject<boolean>(false);
  private readonly uninstalling$ = new BehaviorSubject<boolean>(false);
  private readonly activating$ = new BehaviorSubject<boolean>(false);
  private readonly deleting$ = new BehaviorSubject<boolean>(false);

  private readonly tag$ = this.entry$.pipe(map((e) => e?.instanceTag));

  protected mappedInstall$ = this.actions.action([Actions.INSTALL], this.installing$, null, null, this.tag$);
  protected mappedUninstall$ = this.actions.action([Actions.UNINSTALL], this.uninstalling$, null, null, this.tag$);
  protected mappedActivate$ = this.actions.action([Actions.ACTIVATE], this.activating$, null, null, this.tag$);
  protected mappedDelete$ = this.actions.action(
    [Actions.DELETE_INSTANCE_VERSION],
    this.deleting$,
    null,
    null,
    this.tag$,
  );

  protected isCreate: boolean;
  protected isInstalled: boolean;
  protected isActive: boolean;
  protected hasProduct: boolean;
  protected product$ = new BehaviorSubject<ManifestKey>(null);

  @ViewChild(BdDialogComponent) private readonly dialog: BdDialogComponent;

  private subscription: Subscription;

  ngOnInit() {
    this.subscription = combineLatest([this.areas.panelRoute$, this.history.history$, this.states.state$]).subscribe(
      ([route, entries, state]) => {
        // Note: basing the selection on an index in the service has some drawbacks, but we can do that now without needing to change a lot in the backend.
        const key = route?.paramMap?.get('key');
        this.state$.next(state);
        if (!key || !entries) {
          this.entry$.next(null);
        } else {
          const entry = entries.find((e) => isEqual(histKey(e), histKeyDecode(key)));
          this.entry$.next(entry);
          this.isCreate = entry.type === HistoryEntryType.CREATE;
          this.isInstalled = !!state?.installedTags?.find((s) => s === entry?.instanceTag);
          this.isActive = state?.activeTag === entry?.instanceTag;
        }
      },
    );
    this.subscription.add(
      this.entry$
        .pipe(
          skipWhile((entry) => !entry),
          switchMap((entry) => this.details.getVersionDetails(entry.instanceTag)),
          catchError(() => of(null)),
        )
        .subscribe((cache) => this.product$.next(cache?.config?.product)),
    );
    this.subscription.add(
      combineLatest([this.product$, this.products.products$]).subscribe(([product, products]) => {
        this.hasProduct = !!products?.find((p) => p.key.name === product?.name && p.key.tag === product?.tag);
      }),
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected doInstall() {
    this.installing$.next(true);
    this.states
      .install(this.entry$.value.instanceTag)
      .pipe(finalize(() => this.installing$.next(false)))
      .subscribe();
  }

  protected doUninstall() {
    this.uninstalling$.next(true);
    this.states
      .uninstall(this.entry$.value.instanceTag)
      .pipe(finalize(() => this.uninstalling$.next(false)))
      .subscribe();
  }

  protected doActivate() {
    this.activating$.next(true);
    this.states
      .activate(this.entry$.value.instanceTag)
      .pipe(finalize(() => this.activating$.next(false)))
      .subscribe();
  }

  protected doExport() {
    this.instances.export(this.entry$.value.instanceTag);
  }

  protected doDelete() {
    this.dialog
      .confirm(
        `Delete Version`,
        `This instance version and all its history will be deleted and <strong>cannot be restored</strong>. Are you sure you want to do this?`,
        'delete',
      )
      .subscribe((r) => {
        if (!r) {
          return;
        }
        this.deleting$.next(true);
        this.instances
          .deleteVersion(this.entry$.value.instanceTag)
          .pipe(finalize(() => this.deleting$.next(false)))
          .subscribe(() => this.areas.closePanel(true));
      });
  }

  protected goToProductPage() {
    const group = this.groups.current$.value.name;
    const product = this.product$.value;
    this.areas.navigateBoth(
      ['products', 'browser', group],
      ['panels', 'products', 'details', product.name, product.tag],
    );
  }
}
