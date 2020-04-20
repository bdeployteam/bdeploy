import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { finalize } from 'rxjs/operators';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { ManifestKey } from '../../../../models/gen.dtos';
import { LoggingService } from '../../../core/services/logging.service';
import { DownloadService } from '../../../shared/services/download.service';
import { compareTags } from '../../../shared/utils/manifest.utils';
import { SoftwareService } from '../../services/software.service';

@Component({
  selector: 'app-software-list',
  templateUrl: './software-list.component.html',
  styleUrls: ['./software-list.component.css']
})
export class SoftwareListComponent implements OnInit {

  private log = this.loggingService.getLogger('SoftwareListComponent');

  private _softwareVersions: ManifestKey[];

  @Input() softwareRepositoryName: string;

  @Input() set softwareVersions(softwareVersions: ManifestKey[]) {
    this._softwareVersions = softwareVersions ? softwareVersions.sort((a, b) => {
      if (a.name === b.name) {
        return -1 * compareTags(a.tag, b.tag);
      } else {
        return a.name.localeCompare(b.name);
      }
    }) : [];
  }
  get softwareVersions() {
    return this._softwareVersions;
  }

  @Output() public deleted = new EventEmitter();

  public exporting: ManifestKey = null;

  constructor(private softwareService: SoftwareService,
     private loggingService: LoggingService,
     private downloadService: DownloadService,
     private authService: AuthenticationService) { }

  ngOnInit() {
  }

  delete(softwareVersion: ManifestKey): void {
    this.softwareService.deleteSoftwareVersion(this.softwareRepositoryName, softwareVersion).subscribe(r => {
      this.log.message('Successfully deleted ' + softwareVersion.name + ':' + softwareVersion.tag);
      this.deleted.emit();
    });
  }

  export(softwareVersion: ManifestKey): void {
    this.exporting = softwareVersion;
    this.softwareService
      .createSoftwareZip(this.softwareRepositoryName, softwareVersion)
      .pipe(finalize(() => (this.exporting = null)))
      .subscribe(token => {
        this.downloadService.download(this.softwareService.downloadSoftware(token));
      });
  }

  public isReadOnly(): boolean {
    return !this.authService.isGlobalAdmin();
  }
}
