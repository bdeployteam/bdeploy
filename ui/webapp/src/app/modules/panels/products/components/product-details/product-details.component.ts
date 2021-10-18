import { Component, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject, combineLatest } from 'rxjs';
import { finalize, map } from 'rxjs/operators';
import { BdDataColumn } from 'src/app/models/data';
import { ApplicationTemplateDescriptor, InstanceTemplateDescriptor, InstanceUsageDto, PluginInfoDto, ProductDto } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { LabelRecord, ProductDetailsService } from '../../services/product-details.service';

const instanceNameColumn: BdDataColumn<InstanceUsageDto> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
};

const instanceTagColumn: BdDataColumn<InstanceUsageDto> = {
  id: 'tag',
  name: 'Ver.',
  data: (r) => r.tag,
  width: '30px',
};

const labelKeyColumn: BdDataColumn<LabelRecord> = {
  id: 'key',
  name: 'Label',
  data: (r) => r.key,
  width: '90px',
};

const labelValueColumn: BdDataColumn<LabelRecord> = {
  id: 'value',
  name: 'Value',
  data: (r) => r.value,
  width: '190px',
};

const appTemplateNameColumn: BdDataColumn<ApplicationTemplateDescriptor> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
  tooltip: (r) => r.description,
};

const instTemplateNameColumn: BdDataColumn<InstanceTemplateDescriptor> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
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
  width: '50px',
};

@Component({
  selector: 'app-product-details',
  templateUrl: './product-details.component.html',
  styleUrls: ['./product-details.component.css'],
  providers: [ProductDetailsService],
})
export class ProductDetailsComponent implements OnInit {
  /* template */ deleting$ = new BehaviorSubject<boolean>(false);
  /* template */ instanceColumns: BdDataColumn<InstanceUsageDto>[] = [instanceNameColumn, instanceTagColumn];
  /* template */ labelColumns: BdDataColumn<LabelRecord>[] = [labelKeyColumn, labelValueColumn];
  /* template */ appTemplColumns: BdDataColumn<ApplicationTemplateDescriptor>[] = [appTemplateNameColumn];
  /* template */ instTemplColumns: BdDataColumn<InstanceTemplateDescriptor>[] = [instTemplateNameColumn];
  /* template */ pluginColumns: BdDataColumn<PluginInfoDto>[] = [pluginNameColumn, pluginVersionColumn, pluginOIDColumn];

  /* template */ loading$ = combineLatest([this.deleting$, this.products.loading$]).pipe(map(([a, b]) => a || b));
  /* template */ preparing$ = new BehaviorSubject<boolean>(false);

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  constructor(
    public products: ProductsService,
    public singleProduct: ProductDetailsService,
    public areas: NavAreasService,
    public auth: AuthenticationService
  ) {}

  ngOnInit(): void {}

  /* template */ doDelete(prod: ProductDto) {
    this.dialog.confirm(`Delete ${prod.key.tag}`, `Are you sure you want to delete version ${prod.key.tag}?`, 'delete').subscribe((r) => {
      if (r) {
        this.deleting$.next(true);
        this.singleProduct
          .delete()
          .pipe(finalize(() => this.deleting$.next(false)))
          .subscribe((_) => {
            this.areas.closePanel();
          });
      }
    });
  }

  /* template */ doDownload() {
    this.preparing$.next(true);
    this.singleProduct
      .download()
      .pipe(finalize(() => this.preparing$.next(false)))
      .subscribe();
  }
}
