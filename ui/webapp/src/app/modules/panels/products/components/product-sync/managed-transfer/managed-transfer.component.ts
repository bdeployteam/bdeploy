import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { MinionMode, ProductDto, ProductTransferDto } from 'src/app/models/gen.dtos';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { ProductsColumnsService } from 'src/app/modules/primary/products/services/products-columns.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';

@Component({
  selector: 'app-managed-transfer',
  templateUrl: './managed-transfer.component.html',
  styleUrls: ['./managed-transfer.component.css'],
})
export class ManagedTransferComponent implements OnInit, OnDestroy {
  /* template */ loading$ = new BehaviorSubject<boolean>(true);
  /* template */ records$ = new BehaviorSubject<ProductDto[]>(null);
  /* template */ server$ = new BehaviorSubject<string>(null);
  /* template */ typeText$ = new BehaviorSubject<string>(null);
  /* template */ selected: ProductDto[] = [];

  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;

  private transfer: ProductTransferDto;
  private subscription: Subscription;

  constructor(
    private areas: NavAreasService,
    private servers: ServersService,
    private products: ProductsService,
    public productColumns: ProductsColumnsService
  ) {
    this.subscription = combineLatest([this.areas.panelRoute$, this.products.products$, this.servers.servers$]).subscribe(([r, p, s]) => {
      if (!r?.params || !r.params['server'] || !r.params['target'] || !p || !s?.length) {
        return;
      }

      const server = r.params['server'];
      const target = r.params['target'];

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
            this.records$.next(x.filter((rp) => !p.find((lp) => lp.key.name === rp.key.name && lp.key.tag === rp.key.tag)));
          } else {
            this.records$.next(p.filter((rp) => !x.find((lp) => lp.key.name === rp.key.name && lp.key.tag === rp.key.tag)));
          }
        });
    });
  }

  ngOnInit(): void {}

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  doTransfer(): void {
    this.transfer.versionsToTransfer = this.selected;
    this.servers.transferProducts(this.transfer).subscribe((_) => {
      this.tb.closePanel();
    });
  }
}
