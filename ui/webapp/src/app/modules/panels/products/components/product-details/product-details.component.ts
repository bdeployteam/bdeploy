import { Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { BehaviorSubject, Observable, Subscription, combineLatest } from 'rxjs';
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
export class ProductDetailsComponent implements OnInit, OnDestroy {
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

  private subscription: Subscription;

  protected allowDeletion$ = new BehaviorSubject<boolean>(false);
  protected deletionButtonDisabledReason$ = new BehaviorSubject<string>('');
  private deleting$ = new BehaviorSubject<boolean>(false);

  private p$ = this.singleProduct.product$.pipe(map((p) => p?.key.name + ':' + p?.key.tag));

  // this one *is* allowed multiple times! so no server action mapping.
  protected preparingBHive$ = new BehaviorSubject<boolean>(false);
  protected mappedDelete$ = this.actions.action([Actions.DELETE_PRODUCT], this.deleting$, null, null, this.p$);
  protected loading$ = combineLatest([this.mappedDelete$, this.products.loading$]).pipe(map(([a, b]) => a || b));

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  ngOnInit(): void {
    this.singleProductPlugins$ = this.singleProduct.getPlugins();
    this.subscription = combineLatest([
      this.auth.isCurrentScopeAdmin$,
      this.singleProduct.usedInLoading$,
      this.singleProduct.getUsedIn(),
    ]).subscribe(([currentScopeAdmin, usedInLoading, usedIn]) => {
      if (!currentScopeAdmin) {
        this.allowDeletion$.next(false);
        this.deletionButtonDisabledReason$.next('You do not have the necesarry permissions to delete a product');
        return;
      }

      if (usedInLoading) {
        this.allowDeletion$.next(false);
        this.deletionButtonDisabledReason$.next('Loading product usage information');
        return;
      }

      const count = usedIn?.length | 0;
      if (count > 0) {
        this.allowDeletion$.next(false);
        if (count === 1) {
          const instanceDto = usedIn[0];
          this.deletionButtonDisabledReason$.next(
            'Product is still in use by version ' + instanceDto.tag + ' of instance ' + instanceDto.name,
          );
        } else {
          const mappedDtos = usedIn.reduce((resultArray, dto) => {
            let key = dto.id;
            let value = resultArray.find((v) => v && v.instanceId === key);
            if (value) {
              value.versions.push(dto);
            } else {
              resultArray.push({ instanceId: key, versions: [dto] });
            }
            return resultArray;
          }, new Array<{ instanceId: string; versions: InstanceUsageDto[] }>());

          const instanceCount = mappedDtos.length;
          if (instanceCount === 1) {
            const onlyEntry = mappedDtos[0];
            this.deletionButtonDisabledReason$.next(
              'Product is still in use by the following versions of instance ' +
                onlyEntry.versions[0].name +
                ': ' +
                onlyEntry.versions.map((dto) => dto.tag).join(', '),
            );
          } else {
            const maxNamedInstances = 3;
            let instanceList = 'Product is still in use by the following instances: ';
            for (let i = 0; i < instanceCount; i++) {
              instanceList += mappedDtos[i].versions[0].name + ', ';
              if (i >= maxNamedInstances) {
                break;
              }
            }
            if (instanceCount > maxNamedInstances) {
              instanceList += 'and ' + (instanceCount - maxNamedInstances) + ' more';
            } else {
              instanceList = instanceList.substring(0, instanceList.length - 2);
            }
            this.deletionButtonDisabledReason$.next(instanceList);
          }
        }
        return;
      }

      this.allowDeletion$.next(true);
      this.deletionButtonDisabledReason$.next('');
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
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

  protected doDownload() {
    this.preparingBHive$.next(true);
    this.singleProduct
      .download()
      .pipe(finalize(() => this.preparingBHive$.next(false)))
      .subscribe();
  }
}
