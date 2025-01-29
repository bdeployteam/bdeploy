import { Component, OnInit, ViewChild, inject } from '@angular/core';
import { BehaviorSubject, Observable, combineLatest } from 'rxjs';
import { finalize, map } from 'rxjs/operators';
import { Actions, PluginInfoDto } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstTemplateData, ProductActionsColumnsService } from 'src/app/modules/core/services/product-actions-columns';
import {
  ProdDtoWithType,
  RepositoryService,
  SwDtoWithType,
  SwPkgCompound,
  SwPkgType,
} from 'src/app/modules/primary/repositories/services/repository.service';
import { SoftwareDetailsService } from '../../services/software-details.service';

@Component({
  selector: 'app-software-details',
  templateUrl: './software-details.component.html',
  styleUrls: ['./software-details.component.css'],
  providers: [SoftwareDetailsService],
  standalone: false,
})
export class SoftwareDetailsComponent implements OnInit {
  protected readonly repository = inject(RepositoryService);
  protected readonly detailsService = inject(SoftwareDetailsService);
  protected readonly areas = inject(NavAreasService);
  protected readonly auth = inject(AuthenticationService);
  protected readonly actions = inject(ActionsService);
  protected readonly SwPkgType = SwPkgType;
  protected readonly productActionColumns = inject(ProductActionsColumnsService);

  protected softwareDetailsPlugins$: Observable<PluginInfoDto[]>;

  private readonly p$ = this.detailsService.softwarePackage$.pipe(map((p) => p?.key.name + ':' + p?.key.tag));

  private readonly deleting$ = new BehaviorSubject<boolean>(false);
  protected preparingBHive$ = new BehaviorSubject<boolean>(false);

  protected mappedDelete$ = this.actions.action([Actions.DELETE_SOFTWARE], this.deleting$, null, null, this.p$);
  protected loading$ = combineLatest([this.mappedDelete$, this.repository.loading$]).pipe(map(([a, b]) => a || b));

  protected isRequiredByProduct$ = this.detailsService.softwarePackage$.pipe(
    map((software) => software.type === SwPkgType.EXTERNAL_SOFTWARE && (software as SwDtoWithType).requiredByProduct),
  );

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  ngOnInit(): void {
    this.softwareDetailsPlugins$ = this.detailsService.getPlugins();
  }

  protected asProduct(sw: SwPkgCompound): ProdDtoWithType {
    if (sw.type === SwPkgType.PRODUCT) {
      return sw as ProdDtoWithType;
    }
    throw new Error('Ooops');
  }

  protected doDelete(software: any) {
    this.dialog
      .confirm(`Delete ${software.key.tag}`, `Are you sure you want to delete version ${software.key.tag}?`, 'delete')
      .subscribe((r) => {
        if (r) {
          this.deleting$.next(true);
          this.detailsService
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
    this.detailsService
      .download()
      .pipe(finalize(() => this.preparingBHive$.next(false)))
      .subscribe();
  }

  protected doDownloadResponseFile = (data: InstTemplateData) => {
    this.detailsService.downloadResponseFile(data, this.asProduct(this.detailsService.softwarePackage$.value).key.tag);
  };
}
