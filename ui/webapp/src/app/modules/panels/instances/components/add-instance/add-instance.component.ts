import { Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { AbstractControl, NgForm } from '@angular/forms';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { InstanceConfiguration, InstancePurpose, ManagedMasterDto, ManifestKey } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { SystemsService } from 'src/app/modules/primary/systems/services/systems.service';

interface ProductRow {
  id: string;
  name: string;
  versions: string[];
}

@Component({
  selector: 'app-add-instance',
  templateUrl: './add-instance.component.html',
})
export class AddInstanceComponent implements OnInit, OnDestroy, DirtyableDialog {
  private groups = inject(GroupsService);
  private instances = inject(InstancesService);
  private areas = inject(NavAreasService);
  private router = inject(Router);
  protected products = inject(ProductsService);
  protected servers = inject(ServersService);
  protected cfg = inject(ConfigService);
  protected systems = inject(SystemsService);

  protected loading$ = new BehaviorSubject<boolean>(true);
  protected config: Partial<InstanceConfiguration> = {
    autoUninstall: true,
    product: { name: null, tag: null },
  };
  protected server: ManagedMasterDto;
  protected selectedProduct: ProductRow;

  protected prodList: ProductRow[] = [];
  protected serverList: ManagedMasterDto[] = [];

  private subscription: Subscription;
  protected isCentral = false;
  protected purposes: InstancePurpose[] = [
    InstancePurpose.PRODUCTIVE,
    InstancePurpose.DEVELOPMENT,
    InstancePurpose.TEST,
  ];
  protected productNames: string[] = [];
  protected serverNames: string[] = [];

  protected systemKeys: ManifestKey[];
  protected systemLabels: string[];
  protected systemSel: ManifestKey;

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;
  @ViewChild('form') public form: NgForm;

  ngOnInit(): void {
    this.subscription = this.areas.registerDirtyable(this, 'panel');
    this.subscription.add(
      this.cfg.isCentral$.subscribe((value) => {
        this.isCentral = value;
      }),
    );
    this.subscription.add(
      this.groups.newId().subscribe((r) => {
        this.config.id = r;
        this.subscription.add(
          this.products.products$.subscribe((products) => {
            products?.forEach((p) => {
              let item = this.prodList.find((x) => x.id === p.key.name);
              if (!item) {
                item = { id: p.key.name, name: p.name, versions: [] };
                this.prodList.push(item);
              }
              item.versions.push(p.key.tag);
            });
            this.productNames = this.prodList.map((p) => p.name);
          }),
        );

        const snap = this.areas.panelRoute$.value;
        const prodKey = snap.queryParamMap.get('productKey');
        const prodTag = snap.queryParamMap.get('productTag');
        if (!!prodKey && !!prodTag) {
          const prod = this.prodList.find((p) => p.id === prodKey);
          if (prod?.versions.find((v) => v === prodTag)) {
            this.selectedProduct = prod;
            this.config.product.name = prodKey;
            this.config.product.tag = prodTag;
          }
        }

        this.loading$.next(false);
      }),
    );

    this.subscription.add(
      this.servers.servers$.subscribe((s) => {
        this.serverList = s;
        this.serverNames = this.serverList.map((c) => `${c.hostName} - ${c.description}`);
      }),
    );

    this.subscription.add(
      this.systems.systems$.subscribe((s) => {
        if (!s?.length) {
          return;
        }
        this.systemKeys = s.map((s) => s.key);
        this.systemLabels = s.map((s) => `${s.config.name} (${s.config.description})`);
      }),
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  public isDirty(): boolean {
    return this.form.dirty;
  }

  public canSave(): boolean {
    return this.form.valid;
  }

  protected onSave(): void {
    this.doSave().subscribe(() => {
      this.areas.navigateBoth(
        ['/instances', 'configuration', this.areas.groupContext$.value, this.config.id],
        ['panels', 'instances', 'settings'],
      );
      this.subscription?.unsubscribe();
    });
  }

  public doSave(): Observable<void> {
    this.loading$.next(true);
    return this.instances.create(this.config, this.server?.hostName).pipe(finalize(() => this.loading$.next(false)));
  }

  protected updateProduct() {
    this.config.product.name = this.selectedProduct.id;
    this.config.product.tag = null;
  }

  protected onSystemChange(value: ManifestKey) {
    this.config.system = value;
  }

  protected delayRevalidateSystem(control: AbstractControl) {
    setTimeout(() => control.updateValueAndValidity());
  }
}
