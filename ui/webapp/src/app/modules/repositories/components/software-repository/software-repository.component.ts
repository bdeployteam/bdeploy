import { Location } from '@angular/common';
import { Component, OnInit, ViewChild } from '@angular/core';
import { MatDialog, MatDialogConfig } from '@angular/material/dialog';
import { MatDrawer } from '@angular/material/sidenav';
import { ActivatedRoute } from '@angular/router';
import { forkJoin } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { RoutingHistoryService } from 'src/app/modules/core/services/routing-history.service';
import { ManifestKey } from '../../../../models/gen.dtos';
import { Logger, LoggingService } from '../../../core/services/logging.service';
import { SoftwareService } from '../../services/software.service';
import { SoftwareRepoFileUploadComponent } from '../software-repo-file-upload/software-repo-file-upload.component';

@Component({
  selector: 'app-software-repository',
  templateUrl: './software-repository.component.html',
  styleUrls: ['./software-repository.component.css']
})
export class SoftwareRepositoryComponent implements OnInit {

  private log: Logger = this.loggingService.getLogger('SoftwareRepositoryComponent');

  @ViewChild('appsidenav', { static: true })
  sidenav: MatDrawer;

  public softwareRepositoryName: string = this.route.snapshot.paramMap.get('name');

  public productPackages: Map<string, ManifestKey[]> = new Map();
  public get productPackageNames(): string[] {return Array.from(this.productPackages.keys()); }

  public externalPackages: Map<string, ManifestKey[]> = new Map();
  public get externalPackageNames(): string[] {return Array.from(this.externalPackages.keys()); }


  public selectedSoftwarePackageName: string;

  loading = false;

  constructor(
    private route: ActivatedRoute,
    private loggingService: LoggingService,
    public location: Location,
    private softwareService: SoftwareService,
    public dialog: MatDialog,
    private authService: AuthenticationService,
    public routingHistoryService:RoutingHistoryService,
  ) { }

  ngOnInit() {
    this.load();
  }

  private load(): void {
    this.loading = true;
    forkJoin({
      products: this.softwareService.listSoftwares(this.softwareRepositoryName, true, false),
      external: this.softwareService.listSoftwares(this.softwareRepositoryName, false, true),
    }).pipe(finalize(() => this.loading = false))
      .subscribe(r => {
        // build products map
        this.productPackages.clear();
        r.products.forEach(key => {
          this.productPackages.set(key.name, this.productPackages.get(key.name) || []);
          this.productPackages.get(key.name).push(key);
        });
        // build external software packages map
        this.externalPackages.clear();
        r.external.forEach(key => {
          this.externalPackages.set(key.name, this.externalPackages.get(key.name) || []);
          this.externalPackages.get(key.name).push(key);
        });

        if (this.selectedSoftwarePackageName && this.productPackages.get(this.selectedSoftwarePackageName) === undefined && this.externalPackages.get(this.selectedSoftwarePackageName) === undefined) {
          this.selectedSoftwarePackageName = null;
        }
        if (this.selectedSoftwarePackageName === null) {
          this.sidenav.close();
        }
      })
  }

  public getSoftwareVersions(name: string): ManifestKey[] {
    const products = this.productPackages.get(name);
    return products ? products : this.externalPackages.get(name);
  }

  public versionDeleted(): void {
    this.load();
  }

  public openSoftwarePackage(softwarePackageName: string): void {
    this.selectedSoftwarePackageName = softwarePackageName;
    this.sidenav.open();
  }

  openUploadDialog() {
    const config = new MatDialogConfig();
    config.width = '70%';
    config.height = '80%';
    config.minWidth = '650px';
    config.minHeight = '550px';
    config.data = this.softwareRepositoryName
    this.dialog.open(SoftwareRepoFileUploadComponent,config).afterClosed().subscribe(e=>this.load());
  }

  public isReadOnly(): boolean {
    return !this.authService.isScopedWrite(this.softwareRepositoryName);
  }

}
