import { Component, OnDestroy, OnInit } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { InstanceConfiguration, InstancePurpose, ManagedMasterDto } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';

interface ProductRow {
  id: string;
  name: string;
  versions: string[];
}

@Component({
  selector: 'app-add-instance',
  templateUrl: './add-instance.component.html',
  styleUrls: ['./add-instance.component.css'],
})
export class AddInstanceComponent implements OnInit, OnDestroy {
  /* template */ loading$ = new BehaviorSubject<boolean>(true);
  /* template */ config: Partial<InstanceConfiguration> = { autoUninstall: true, product: { name: null, tag: null } };
  /* template */ server: ManagedMasterDto;
  /* template */ selectedProduct: ProductRow;

  /* template */ prodList: ProductRow[] = [];
  /* template */ serverList: ManagedMasterDto[] = [];

  private subscription: Subscription;
  /* template */ public isCentral: boolean = false;

  constructor(
    private groups: GroupsService,
    private instances: InstancesService,
    public products: ProductsService,
    private areas: NavAreasService,
    public servers: ServersService,
    public cfg: ConfigService
  ) {}

  ngOnInit(): void {
    this.subscription = this.groups
      .newUuid()
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe((r) => (this.config.uuid = r));
    this.subscription.add(
      this.cfg.isCentral$.subscribe((value) => {
        this.isCentral = value;
      })
    );

    this.subscription.add(
      this.products.products$.subscribe((r) => {
        r.forEach((p) => {
          let item = this.prodList.find((x) => x.id === p.key.name);
          if (!item) {
            item = { id: p.key.name, name: p.name, versions: [] };
            this.prodList.push(item);
          }
          item.versions.push(p.key.tag);
        });

        const snap = this.areas.panelRoute$.value;
        const prodKey = snap.queryParamMap.get('productKey');
        const prodTag = snap.queryParamMap.get('productTag');
        if (!!prodKey && !!prodTag) {
          const prod = this.prodList.find((p) => p.id === prodKey);
          if (!!prod) {
            if (!!prod.versions.find((v) => v === prodTag)) {
              this.selectedProduct = prod;
              this.config.product.name = prodKey;
              this.config.product.tag = prodTag;
            }
          }
        }
      })
    );

    this.subscription.add(this.servers.servers$.subscribe((s) => (this.serverList = s)));
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  onSave(): void {
    this.loading$.next(true);
    this.instances
      .create(this.config, this.server?.hostName)
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe((_) => {
        this.areas.closePanel();
      });
  }

  /* template */ getPurposes(): InstancePurpose[] {
    return [InstancePurpose.PRODUCTIVE, InstancePurpose.DEVELOPMENT, InstancePurpose.TEST];
  }

  /* template */ updateProduct() {
    this.config.product.name = this.selectedProduct.id;
    this.config.product.tag = null;
  }

  /* template */ getProductNames() {
    return this.prodList.map((p) => p.name);
  }

  /* template */ getServerNames() {
    return this.serverList.map((s) => `${s.hostName} - ${s.description}`);
  }
}
