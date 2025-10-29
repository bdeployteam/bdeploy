import { Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject, combineLatest, Observable, Subscription } from 'rxjs';
import { finalize, map } from 'rxjs/operators';
import { BdDataColumn } from 'src/app/models/data';
import { Actions, InstanceUsageDto, ManifestKey, PluginInfoDto, ProductDto } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstTemplateData, ProductActionsColumnsService } from 'src/app/modules/core/services/product-actions-columns';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { ProductDetailsService } from '../../services/product-details.service';

import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { MatIcon } from '@angular/material/icon';
import { BdIdentifierComponent } from '../../../../core/components/bd-identifier/bd-identifier.component';
import { BdExpandButtonComponent } from '../../../../core/components/bd-expand-button/bd-expand-button.component';
import { BdDataDisplayComponent } from '../../../../core/components/bd-data-display/bd-data-display.component';
import { BdNoDataComponent } from '../../../../core/components/bd-no-data/bd-no-data.component';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { BdPanelButtonComponent } from '../../../../core/components/bd-panel-button/bd-panel-button.component';
import { MatTooltip } from '@angular/material/tooltip';
import { AsyncPipe } from '@angular/common';

const instanceNameColumn: BdDataColumn<InstanceUsageDto, string> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
  isId: true,
};

const instanceTagColumn: BdDataColumn<InstanceUsageDto, string> = {
  id: 'tag',
  name: 'Ver.',
  data: (r) => r.tag,
  isId: true,
  width: '30px',
};

const refNameColumn: BdDataColumn<ManifestKey, string> = {
  id: 'name',
  name: 'Name',
  data: (r) => r.name,
  isId: true,
};

const refTagColumn: BdDataColumn<ManifestKey, string> = {
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
  imports: [
    BdDialogComponent,
    BdDialogToolbarComponent,
    BdDialogContentComponent,
    MatIcon,
    BdIdentifierComponent,
    BdExpandButtonComponent,
    BdDataDisplayComponent,
    BdNoDataComponent,
    MatProgressSpinner,
    BdButtonComponent,
    BdPanelButtonComponent,
    MatTooltip,
    AsyncPipe,
  ],
})
export class ProductDetailsComponent implements OnInit, OnDestroy {
  private readonly actions = inject(ActionsService);
  protected readonly products = inject(ProductsService);
  protected readonly singleProduct = inject(ProductDetailsService);
  protected readonly areas = inject(NavAreasService);
  protected readonly auth = inject(AuthenticationService);
  protected readonly productActionColumns = inject(ProductActionsColumnsService);

  protected readonly instanceColumns: BdDataColumn<InstanceUsageDto, unknown>[] = [
    instanceNameColumn,
    instanceTagColumn,
  ];
  protected readonly refColumns: BdDataColumn<ManifestKey, unknown>[] = [refNameColumn, refTagColumn];
  protected singleProductPlugins$: Observable<PluginInfoDto[]>;

  private subscription: Subscription;

  protected allowDeletion$ = new BehaviorSubject<boolean>(false);
  protected deletionButtonDisabledReason$ = new BehaviorSubject<string>('');
  private readonly deleting$ = new BehaviorSubject<boolean>(false);

  private readonly p$ = this.singleProduct.product$.pipe(map((p) => `${p?.key.name}:${p?.key.tag}`));

  // this one *is* allowed multiple times! so no server action mapping.
  protected preparingBHive$ = new BehaviorSubject<boolean>(false);
  protected mappedDelete$ = this.actions.action([Actions.DELETE_PRODUCT], this.deleting$, null, null, this.p$);
  protected loading$ = combineLatest([this.mappedDelete$, this.products.loading$]).pipe(map(([a, b]) => a || b));
  protected resetWhen$: Observable<boolean>;

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  constructor() {
    this.resetWhen$ = this.singleProduct.product$.pipe(map((productDto) => !!productDto));
  }

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
            `Product is still in use by version ${instanceDto.tag} of instance ${instanceDto.name}`
          );
        } else {
          const mappedDtos = usedIn.reduce((resultArray, dto) => {
            const key = dto.id;
            const value = resultArray.find((v) => v && v.instanceId === key);
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
              `Product is still in use by the following versions of instance ${
                onlyEntry.versions[0].name
              }: ${onlyEntry.versions.map((dto) => dto.tag).join(', ')}`
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

  protected doDownloadResponseFile = (data: InstTemplateData) => {
    this.dialog
      .confirm('Include defaults?', 'Do you want to include variables that have a default value in the response file?')
      .subscribe((result) => this.products.downloadResponseFile(data, this.singleProduct.productTag$.value, result));
  };
}
