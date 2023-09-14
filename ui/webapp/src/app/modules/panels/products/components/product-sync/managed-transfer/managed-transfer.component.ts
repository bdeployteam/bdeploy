import { Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { BehaviorSubject, Subscription, combineLatest, finalize, map, of } from 'rxjs';
import { Actions, MinionMode, ProductDto, ProductTransferDto } from 'src/app/models/gen.dtos';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { ProductsColumnsService } from 'src/app/modules/primary/products/services/products-columns.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';

@Component({
  selector: 'app-managed-transfer',
  templateUrl: './managed-transfer.component.html',
})
export class ManagedTransferComponent implements OnInit, OnDestroy {
  private areas = inject(NavAreasService);
  private servers = inject(ServersService);
  private products = inject(ProductsService);
  protected productColumns = inject(ProductsColumnsService);

  protected loading$ = new BehaviorSubject<boolean>(true);
  protected records$ = new BehaviorSubject<ProductDto[]>(null);
  protected server$ = new BehaviorSubject<string>(null);
  protected typeText$ = new BehaviorSubject<string>(null);
  protected selected$ = new BehaviorSubject<ProductDto[]>([]);

  private actions = inject(ActionsService);
  protected mappedTransfer$;

  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;

  private transfer: ProductTransferDto;
  private subscription: Subscription;

  ngOnInit() {
    this.subscription = combineLatest([
      this.areas.panelRoute$,
      this.products.products$,
      this.servers.servers$,
    ]).subscribe(([r, p, s]) => {
      if (!r?.params || !r.params['server'] || !r.params['target'] || !p || !s?.length) {
        return;
      }

      const server = r.params['server'];
      const target = r.params['target'];

      this.mappedTransfer$ = this.actions.action(
        target === MinionMode.CENTRAL ? [Actions.TRANSFER_PRODUCT_CENTRAL] : [Actions.TRANSFER_PRODUCT_MANAGED],
        of(false),
        null,
        null,
        this.selected$.pipe(map((p) => p.map((x) => `${x.key.name}:${x.key.tag}`)))
      );

      this.server$.next(server);
      this.typeText$.next(target === MinionMode.CENTRAL ? 'Download from' : 'Upload to');

      if (target === MinionMode.CENTRAL) {
        this.transfer = {
          sourceMode: MinionMode.MANAGED,
          sourceServer: server,
          targetMode: MinionMode.CENTRAL,
          targetServer: null,
          versionsToTransfer: [],
        };
      } else {
        this.transfer = {
          sourceMode: MinionMode.CENTRAL,
          sourceServer: null,
          targetMode: MinionMode.MANAGED,
          targetServer: server,
          versionsToTransfer: [],
        };
      }

      this.servers
        .getRemoteProducts(server)
        .pipe(finalize(() => this.loading$.next(false)))
        .subscribe((x) => {
          if (target === MinionMode.CENTRAL) {
            this.records$.next(
              x.filter((rp) => !p.find((lp) => lp.key.name === rp.key.name && lp.key.tag === rp.key.tag))
            );
          } else {
            this.records$.next(
              p.filter((rp) => !x.find((lp) => lp.key.name === rp.key.name && lp.key.tag === rp.key.tag))
            );
          }
        });
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected doTransfer(): void {
    this.transfer.versionsToTransfer = this.selected$.value;
    this.servers.transferProducts(this.transfer).subscribe(() => {
      this.tb.closePanel();
    });
  }
}
