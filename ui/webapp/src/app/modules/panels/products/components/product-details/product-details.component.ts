import { Component, OnInit, ViewChild, inject } from '@angular/core';
import { BehaviorSubject, Observable, combineLatest } from 'rxjs';
import { finalize, map } from 'rxjs/operators';
import { BdDataColumn } from 'src/app/models/data';
import {
  Actions,
  FlattenedApplicationTemplateConfiguration,
  FlattenedInstanceTemplateConfiguration,
  InstanceUsageDto,
  ManifestKey,
  PluginInfoDto,
  ProductDto,
} from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { LabelRecord, ProductDetailsService } from '../../services/product-details.service';

const instanceNameColumn: BdDataColumn<InstanceUsageDto> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
  isId: true,
};

const instanceTagColumn: BdDataColumn<InstanceUsageDto> = {
  id: 'tag',
  name: 'Ver.',
  data: (r) => r.tag,
  isId: true,
  width: '30px',
};

const labelKeyColumn: BdDataColumn<LabelRecord> = {
  id: 'key',
  name: 'Label',
  data: (r) => r.key,
  isId: true,
  width: '90px',
};

const labelValueColumn: BdDataColumn<LabelRecord> = {
  id: 'value',
  name: 'Value',
  data: (r) => r.value,
  width: '190px',
};

const appTemplateNameColumn: BdDataColumn<FlattenedApplicationTemplateConfiguration> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
  isId: true,
  tooltip: (r) => r.description,
};

const instTemplateNameColumn: BdDataColumn<FlattenedInstanceTemplateConfiguration> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
  isId: true,
  tooltip: (r) => r.description,
};

const pluginNameColumn: BdDataColumn<PluginInfoDto> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
  width: '130px',
};

const pluginVersionColumn: BdDataColumn<PluginInfoDto> = {
  id: 'description',
  name: 'Description',
  data: (r) => r.version,
  width: '100px',
};

const pluginOIDColumn: BdDataColumn<PluginInfoDto> = {
  id: 'oid',
  name: 'OID',
  data: (r) => r.id.id,
  isId: true,
  width: '50px',
};

const refNameColumn: BdDataColumn<ManifestKey> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
  isId: true,
};

const refTagColumn: BdDataColumn<ManifestKey> = {
  id: 'tag',
  name: 'Ver.',
  data: (r) => r.tag,
  isId: true,
};

@Component({
  selector: 'app-product-details',
  templateUrl: './product-details.component.html',
  styleUrls: ['./product-details.component.css'],
  providers: [ProductDetailsService],
})
export class ProductDetailsComponent implements OnInit {
  private actions = inject(ActionsService);
  protected products = inject(ProductsService);
  protected singleProduct = inject(ProductDetailsService);
  protected areas = inject(NavAreasService);
  protected auth = inject(AuthenticationService);

  protected instanceColumns: BdDataColumn<InstanceUsageDto>[] = [instanceNameColumn, instanceTagColumn];
  protected labelColumns: BdDataColumn<LabelRecord>[] = [labelKeyColumn, labelValueColumn];
  protected appTemplColumns: BdDataColumn<FlattenedApplicationTemplateConfiguration>[] = [appTemplateNameColumn];
  protected instTemplColumns: BdDataColumn<FlattenedInstanceTemplateConfiguration>[] = [instTemplateNameColumn];
  protected pluginColumns: BdDataColumn<PluginInfoDto>[] = [pluginNameColumn, pluginVersionColumn, pluginOIDColumn];

  protected refColumns: BdDataColumn<ManifestKey>[] = [refNameColumn, refTagColumn];
  protected singleProductPlugins$: Observable<PluginInfoDto[]>;

  private deleting$ = new BehaviorSubject<boolean>(false);
  private preparingCont$ = new BehaviorSubject<boolean>(false);

  private p$ = this.singleProduct.product$.pipe(map((p) => p?.key.name + ':' + p?.key.tag));

  // this one *is* allowed multiple times! so no server action mapping.
  protected preparingBHive$ = new BehaviorSubject<boolean>(false);
  protected mappedDelete$ = this.actions.action([Actions.DELETE_PRODUCT], this.deleting$, null, null, this.p$);
  protected mappedPrepC$ = this.actions.action([Actions.DOWNLOAD_PRODUCT_C], this.preparingCont$, null, null, this.p$);
  protected loading$ = combineLatest([this.mappedDelete$, this.products.loading$]).pipe(map(([a, b]) => a || b));

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  ngOnInit(): void {
    this.singleProductPlugins$ = this.singleProduct.getPlugins();
  }

  protected doDelete(prod: ProductDto) {
    this.dialog
      .confirm(`Delete ${prod.key.tag}`, `Are you sure you want to delete version ${prod.key.tag}?`, 'delete')
      .subscribe((r) => {
        if (r) {
          this.deleting$.next(true);
          this.singleProduct
            .delete()
            .pipe(finalize(() => this.deleting$.next(false)))
            .subscribe(() => {
              this.areas.closePanel();
            });
        }
      });
  }

  protected doDownload(original: boolean) {
    const preparing$ = original ? this.preparingCont$ : this.preparingBHive$;
    preparing$.next(true);
    this.singleProduct
      .download(original)
      .pipe(finalize(() => preparing$.next(false)))
      .subscribe();
  }
}
