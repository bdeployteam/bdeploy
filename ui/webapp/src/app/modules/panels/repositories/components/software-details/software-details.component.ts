import { Component, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject, combineLatest, Observable } from 'rxjs';
import { finalize, map } from 'rxjs/operators';
import { BdDataColumn } from 'src/app/models/data';
import {
  FlattenedApplicationTemplateConfiguration,
  FlattenedInstanceTemplateConfiguration,
  PluginInfoDto,
} from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { RepositoryService } from 'src/app/modules/primary/repositories/services/repository.service';
import { SoftwareDetailsService } from '../../services/software-details.service';

interface LabelRecord {
  key: string;
  value: string;
}

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

const appTemplateNameColumn: BdDataColumn<FlattenedApplicationTemplateConfiguration> =
  {
    id: 'name',
    name: 'Name',
    data: (r) => r.name,
    tooltip: (r) => r.description,
  };

const instTemplateNameColumn: BdDataColumn<FlattenedInstanceTemplateConfiguration> =
  {
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
  selector: 'app-software-details',
  templateUrl: './software-details.component.html',
  styleUrls: ['./software-details.component.css'],
  providers: [SoftwareDetailsService],
})
export class SoftwareDetailsComponent implements OnInit {
  /* template */ deleting$ = new BehaviorSubject<boolean>(false);
  /* template */ labelColumns: BdDataColumn<LabelRecord>[] = [
    labelKeyColumn,
    labelValueColumn,
  ];
  /* template */ appTemplColumns: BdDataColumn<FlattenedApplicationTemplateConfiguration>[] =
    [appTemplateNameColumn];
  /* template */ instTemplColumns: BdDataColumn<FlattenedInstanceTemplateConfiguration>[] =
    [instTemplateNameColumn];
  /* template */ pluginColumns: BdDataColumn<PluginInfoDto>[] = [
    pluginNameColumn,
    pluginVersionColumn,
    pluginOIDColumn,
  ];

  /* template */ loading$ = combineLatest([
    this.deleting$,
    this.repository.loading$,
  ]).pipe(map(([a, b]) => a || b));
  /* template */ preparing$ = new BehaviorSubject<boolean>(false);
  /* template */ softwareDetailsPlugins$: Observable<PluginInfoDto[]>;

  isRequiredByProduct$ = combineLatest([
    this.detailsService.softwarePackage$,
    this.repository.products$,
  ]).pipe(
    map(([software, products]) => [
      software,
      products.reduce((acc, product) => acc.concat(product.references), []),
    ]),
    map(([software, references]) => {
      const isExternalSoftware = software?.type === 'External Software';
      return (
        isExternalSoftware &&
        references.some(
          (reference) =>
            software.key.name === reference.name &&
            software.key.tag === reference.tag
        )
      );
    })
  );

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  constructor(
    public repository: RepositoryService,
    public detailsService: SoftwareDetailsService,
    public areas: NavAreasService,
    public auth: AuthenticationService
  ) {}

  ngOnInit(): void {
    this.softwareDetailsPlugins$ = this.detailsService.getPlugins();
  }

  /* template */ doDelete(software: any) {
    this.dialog
      .confirm(
        `Delete ${software.key.tag}`,
        `Are you sure you want to delete version ${software.key.tag}?`,
        'delete'
      )
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

  /* template */ doDownload() {
    this.preparing$.next(true);
    this.detailsService
      .download()
      .pipe(finalize(() => this.preparing$.next(false)))
      .subscribe();
  }
}
