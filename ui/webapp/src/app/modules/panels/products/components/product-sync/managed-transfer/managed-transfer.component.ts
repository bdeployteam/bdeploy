import { Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject, combineLatest, finalize, map, Observable, of, Subscription } from 'rxjs';
import { Actions, MinionMode, ProductDto, ProductTransferDto } from 'src/app/models/gen.dtos';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { ProductsColumnsService } from 'src/app/modules/primary/products/services/products-columns.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { BdDialogComponent } from '../../../../../core/components/bd-dialog/bd-dialog.component';

import { BdDialogContentComponent } from '../../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdDataTableComponent } from '../../../../../core/components/bd-data-table/bd-data-table.component';
import { BdButtonComponent } from '../../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';

@Component({
  selector: 'app-managed-transfer',
  templateUrl: './managed-transfer.component.html',
  imports: [
    BdDialogComponent,
    BdDialogToolbarComponent,
    BdDialogContentComponent,
    BdDataTableComponent,
    BdButtonComponent,
    AsyncPipe,
  ],
})
export class ManagedTransferComponent implements OnInit, OnDestroy {
  private readonly areas = inject(NavAreasService);
  private readonly servers = inject(ServersService);
  private readonly products = inject(ProductsService);
  private readonly actions = inject(ActionsService);
  protected readonly productColumns = inject(ProductsColumnsService);

  protected loading$ = new BehaviorSubject<boolean>(true);
  protected records$ = new BehaviorSubject<ProductDto[]>(null);
  protected server$ = new BehaviorSubject<string>(null);
  protected typeText$ = new BehaviorSubject<string>(null);
  protected selected$ = new BehaviorSubject<ProductDto[]>([]);

  protected mappedTransfer$: Observable<boolean>;

  @ViewChild(BdDialogToolbarComponent) private readonly tb: BdDialogToolbarComponent;

  private transfer: ProductTransferDto;
  private subscription: Subscription;

  ngOnInit() {
    this.subscription = combineLatest([
      this.areas.panelRoute$,
      this.products.products$,
      this.servers.servers$,
    ]).subscribe(([r, p, s]) => {
      if (!r?.params?.['server'] || !r.params['target'] || !p || !s?.length) {
        return;
      }

      const server = r.params['server'];
      const target = r.params['target'];

      this.mappedTransfer$ = this.actions.action(
        target === MinionMode.CENTRAL ? [Actions.TRANSFER_PRODUCT_CENTRAL] : [Actions.TRANSFER_PRODUCT_MANAGED],
        of(false),
        null,
        null,
        this.selected$.pipe(map((productDto) => productDto.map((x) => `${x.key.name}:${x.key.tag}`)))
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
              x.filter((rp) => !p.some((lp) => lp.key.name === rp.key.name && lp.key.tag === rp.key.tag))
            );
          } else {
            this.records$.next(
              p.filter((rp) => !x.some((lp) => lp.key.name === rp.key.name && lp.key.tag === rp.key.tag))
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
