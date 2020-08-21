import { Location } from '@angular/common';
import { Component, OnInit, ViewChild } from '@angular/core';
import { MatDialog, MatDialogConfig } from '@angular/material/dialog';
import { MatDrawer } from '@angular/material/sidenav';
import { ActivatedRoute } from '@angular/router';
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

  public softwarePackages: Map<string, ManifestKey[]> = new Map();
  public get softwarePackageNames(): string[] {return Array.from(this.softwarePackages.keys()); }
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
    this.loadSoftwares();
  }

  private loadSoftwares(): void {
    this.softwareService.listSoftwares(this.softwareRepositoryName).subscribe(
      manifestKeys => {
        this.log.debug('got ' + manifestKeys.length + ' manifests');

        this.softwarePackages = new Map();
        manifestKeys.forEach(key => {
          this.softwarePackages.set(key.name, this.softwarePackages.get(key.name) || []);
          this.softwarePackages.get(key.name).push(key);
        });
        if (this.selectedSoftwarePackageName && this.softwarePackages.get(this.selectedSoftwarePackageName) === undefined) {
          this.selectedSoftwarePackageName = null;
        }
        if (this.selectedSoftwarePackageName === null) {
          this.sidenav.close();
        }

    });
  }

  public versionDeleted(): void {
    this.loadSoftwares();
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
    this.dialog.open(SoftwareRepoFileUploadComponent,config).afterClosed().subscribe(e=>this.loadSoftwares());
  }

  public isReadOnly(): boolean {
    return !this.authService.isScopedWrite(this.softwareRepositoryName);
  }

}
