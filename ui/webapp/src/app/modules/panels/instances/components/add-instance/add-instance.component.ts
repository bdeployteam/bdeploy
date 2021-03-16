import { Component, OnInit } from '@angular/core';
import { BehaviorSubject, forkJoin } from 'rxjs';
import { finalize, first, skipWhile } from 'rxjs/operators';
import { InstanceConfiguration, InstancePurpose } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';

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
export class AddInstanceComponent implements OnInit {
  /* template */ loading$ = new BehaviorSubject<boolean>(true);
  /* template */ config: Partial<InstanceConfiguration> = { autoUninstall: true, product: { name: null, tag: null } };
  /* template */ server: string;
  /* template */ selectedProduct: ProductRow;

  /* template */ prodList: ProductRow[] = [];

  constructor(private groups: GroupsService, private instances: InstancesService, public products: ProductsService, private areas: NavAreasService) {}

  ngOnInit(): void {
    forkJoin({
      uuid: this.groups.newUuid(),
      product: this.products.products$.pipe(
        skipWhile((v) => !v?.length),
        first()
      ),
    })
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe((r) => {
        this.config.uuid = r.uuid;
        r.product.forEach((p) => {
          let item = this.prodList.find((x) => x.id === p.key.name);
          if (!item) {
            item = { id: p.key.name, name: p.name, versions: [] };
            this.prodList.push(item);
          }
          item.versions.push(p.key.tag);
        });
      });
  }

  onSave(): void {
    this.loading$.next(true);
    this.instances
      .create(this.config, this.server)
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
}
