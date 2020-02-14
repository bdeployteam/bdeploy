import { Location } from '@angular/common';
import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { MediaChange, MediaObserver } from '@angular/flex-layout';
import { MatDialog, MatDialogConfig } from '@angular/material/dialog';
import { MatDrawer } from '@angular/material/sidenav';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { ManifestKey } from '../../../../models/gen.dtos';
import { Logger, LoggingService } from '../../../core/services/logging.service';
import { FileUploadComponent } from '../../../shared/components/file-upload/file-upload.component';
import { SoftwareService } from '../../services/software.service';

@Component({
  selector: 'app-software-repository',
  templateUrl: './software-repository.component.html',
  styleUrls: ['./software-repository.component.css']
})
export class SoftwareRepositoryComponent implements OnInit, OnDestroy {

  private log: Logger = this.loggingService.getLogger('SoftwareRepositoryComponent');

  @ViewChild('appsidenav', { static: true })
  sidenav: MatDrawer;

  public softwareRepositoryName: string = this.route.snapshot.paramMap.get('name');

  public softwarePackages: Map<string, ManifestKey[]> = new Map();
  public get softwarePackageNames(): string[] {return Array.from(this.softwarePackages.keys()); }
  public selectedSoftwarePackageName: string;

  private subscription: Subscription;
  private grid = new Map([['xs', 1], ['sm', 1], ['md', 2], ['lg', 3], ['xl', 5]]);

  loading = false;
  columns = 3; // calculated number of columns

  constructor(
    private mediaObserver: MediaObserver,
    private route: ActivatedRoute,
    private loggingService: LoggingService,
    public location: Location,
    private softwareService: SoftwareService,
    public dialog: MatDialog,
  ) { }

  ngOnInit() {
    this.subscription = this.mediaObserver.media$.subscribe((change: MediaChange) => {
      this.columns = this.grid.get(change.mqAlias);
    });
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

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
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
    config.width = '80%';
    config.height = '60%';
    config.minWidth = '650px';
    config.minHeight = '550px';
    config.data = {
      title: 'Upload Software Packages',
      headerMessage: 'Upload software packages into this software repository. The selected archive may contain any new software package or a new version of an existing software package.',
      url: this.softwareService.getSoftwareUploadUrl(this.softwareRepositoryName),
      mimeTypes: ['application/x-zip-compressed', 'application/zip'],
      mimeTypeErrorMessage: 'Only ZIP files can be uploaded.'
    };
    this.dialog
      .open(FileUploadComponent, config)
      .afterClosed()
      .subscribe(e => {
        this.loadSoftwares();
      });
  }

}
