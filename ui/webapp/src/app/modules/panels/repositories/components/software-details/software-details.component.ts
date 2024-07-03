import { Component, OnInit, ViewChild, inject } from '@angular/core';
import { BehaviorSubject, Observable, combineLatest } from 'rxjs';
import { finalize, map } from 'rxjs/operators';
import { BdDataColumn } from 'src/app/models/data';
import {
  Actions,
  FlattenedApplicationTemplateConfiguration,
  FlattenedInstanceTemplateConfiguration,
  PluginInfoDto,
} from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import {
  ProdDtoWithType,
  RepositoryService,
  SwPkgCompound,
} from 'src/app/modules/primary/repositories/services/repository.service';
import { SoftwareDetailsService } from '../../services/software-details.service';

interface LabelRecord {
  key: string;
  value: string;
}

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

@Component({
  selector: 'app-software-details',
  templateUrl: './software-details.component.html',
  styleUrls: ['./software-details.component.css'],
  providers: [SoftwareDetailsService],
})
export class SoftwareDetailsComponent implements OnInit {
  protected readonly repository = inject(RepositoryService);
  protected readonly detailsService = inject(SoftwareDetailsService);
  protected readonly areas = inject(NavAreasService);
  protected readonly auth = inject(AuthenticationService);
  protected readonly actions = inject(ActionsService);

  protected readonly labelColumns: BdDataColumn<LabelRecord>[] = //
    [labelKeyColumn, labelValueColumn];
  protected readonly appTemplColumns: BdDataColumn<FlattenedApplicationTemplateConfiguration>[] = //
    [appTemplateNameColumn];
  protected readonly instTemplColumns: BdDataColumn<FlattenedInstanceTemplateConfiguration>[] = //
    [instTemplateNameColumn];
  protected readonly pluginColumns: BdDataColumn<PluginInfoDto>[] = //
    [pluginNameColumn, pluginVersionColumn, pluginOIDColumn];
  protected softwareDetailsPlugins$: Observable<PluginInfoDto[]>;

  private readonly p$ = this.detailsService.softwarePackage$.pipe(map((p) => p?.key.name + ':' + p?.key.tag));

  private readonly deleting$ = new BehaviorSubject<boolean>(false);
  protected preparingBHive$ = new BehaviorSubject<boolean>(false);

  protected mappedDelete$ = this.actions.action([Actions.DELETE_SOFTWARE], this.deleting$, null, null, this.p$);
  protected loading$ = combineLatest([this.mappedDelete$, this.repository.loading$]).pipe(map(([a, b]) => a || b));

  isRequiredByProduct$ = combineLatest([this.detailsService.softwarePackage$, this.repository.products$]).pipe(
    map(([software, products]) => {
      const references = products.reduce((acc, product) => acc.concat(product.references), []);
      const isExternalSoftware = software?.type === 'External Software';
      return (
        isExternalSoftware &&
        references.some((reference) => software.key.name === reference.name && software.key.tag === reference.tag)
      );
    }),
  );

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  ngOnInit(): void {
    this.softwareDetailsPlugins$ = this.detailsService.getPlugins();
  }

  protected asProduct(sw: SwPkgCompound): ProdDtoWithType {
    if (sw.type === 'Product') {
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
}
