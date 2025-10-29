import { Component, inject, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject, combineLatest, Observable } from 'rxjs';
import { finalize, map } from 'rxjs/operators';
import { Actions, PluginInfoDto } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstTemplateData, ProductActionsColumnsService } from 'src/app/modules/core/services/product-actions-columns';
import {
  SwPkgType,
  RepositoryService, SwRepositoryEntry
} from 'src/app/modules/primary/repositories/services/repository.service';
import { SoftwareDetailsService } from '../../services/software-details.service';

import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { MatIcon } from '@angular/material/icon';
import { BdIdentifierComponent } from '../../../../core/components/bd-identifier/bd-identifier.component';
import { BdExpandButtonComponent } from '../../../../core/components/bd-expand-button/bd-expand-button.component';
import { BdDataDisplayComponent } from '../../../../core/components/bd-data-display/bd-data-display.component';
import { BdNoDataComponent } from '../../../../core/components/bd-no-data/bd-no-data.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { BdPanelButtonComponent } from '../../../../core/components/bd-panel-button/bd-panel-button.component';
import { AsyncPipe } from '@angular/common';

@Component({
  selector: 'app-software-details',
  templateUrl: './software-details.component.html',
  styleUrls: ['./software-details.component.css'],
  providers: [SoftwareDetailsService],
  imports: [
    BdDialogComponent,
    BdDialogToolbarComponent,
    BdDialogContentComponent,
    MatIcon,
    BdIdentifierComponent,
    BdExpandButtonComponent,
    BdDataDisplayComponent,
    BdNoDataComponent,
    BdButtonComponent,
    BdPanelButtonComponent,
    AsyncPipe,
  ],
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

  private readonly p$ = this.detailsService.softwarePackage$.pipe(map((p) => `${p?.key.name}:${p?.key.tag}`));

  private readonly deleting$ = new BehaviorSubject<boolean>(false);
  protected preparingBHive$ = new BehaviorSubject<boolean>(false);

  protected mappedDelete$ = this.actions.action([Actions.DELETE_SOFTWARE], this.deleting$, null, null, this.p$);
  protected loading$ = combineLatest([this.mappedDelete$, this.repository.loading$]).pipe(map(([a, b]) => a || b));

  protected isRequiredByProduct$ = this.detailsService.softwarePackage$.pipe(
    map((software) => software.requiredByProduct)
  );
  protected resetWhen$: Observable<boolean>;

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  constructor() {
    this.resetWhen$ = this.detailsService.softwarePackage$.pipe(map((swPkgCompound) => !!swPkgCompound));
  }

  ngOnInit(): void {
    this.softwareDetailsPlugins$ = this.detailsService.getPlugins();
  }

  protected doDelete(software: SwRepositoryEntry) {
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
    this.dialog
      .confirm('Include defaults?', 'Do you want to include variables that have a default value in the response file?')
      .subscribe((result) =>
        this.detailsService.downloadResponseFile(data, this.detailsService.softwarePackage$.value.key.tag, result)
      );
  };
}
