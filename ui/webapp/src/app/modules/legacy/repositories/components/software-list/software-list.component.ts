import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { finalize } from 'rxjs/operators';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { SoftwarePackageGroup } from 'src/app/modules/legacy/core/models/software.model';
import { MessageBoxMode } from 'src/app/modules/legacy/shared/components/messagebox/messagebox.component';
import { MessageboxService } from 'src/app/modules/legacy/shared/services/messagebox.service';
import { ManifestKey, OperatingSystem } from '../../../../../models/gen.dtos';
import { DownloadService } from '../../../../core/services/download.service';
import { LoggingService } from '../../../../core/services/logging.service';
import { SoftwareService } from '../../services/software.service';

@Component({
  selector: 'app-software-list',
  templateUrl: './software-list.component.html',
  styleUrls: ['./software-list.component.css'],
})
export class SoftwareListComponent implements OnInit {
  private log = this.loggingService.getLogger('SoftwareListComponent');

  @Input() softwareRepositoryName: string;
  @Input() activeOs: OperatingSystem;
  @Input() softwarePackageGroup: SoftwarePackageGroup;
  @Output() public deleted = new EventEmitter();

  public exporting: ManifestKey = null;

  constructor(
    private messageBoxService: MessageboxService,
    private softwareService: SoftwareService,
    private loggingService: LoggingService,
    private downloadService: DownloadService,
    private authService: AuthenticationService
  ) {}

  ngOnInit() {}

  delete(softwareVersion: ManifestKey): void {
    this.messageBoxService
      .open({
        title: 'Delete',
        message: 'Do you really want to delete the software version ' + softwareVersion.name + ':' + softwareVersion.tag + '?',
        mode: MessageBoxMode.CONFIRM,
      })
      .subscribe((r) => {
        if (r) {
          this.softwareService.deleteSoftwareVersion(this.softwareRepositoryName, softwareVersion).subscribe((_) => {
            this.deleted.emit();
          });
        }
      });
  }

  export(softwareVersion: ManifestKey): void {
    this.exporting = softwareVersion;
    this.softwareService
      .createSoftwareZip(this.softwareRepositoryName, softwareVersion)
      .pipe(finalize(() => (this.exporting = null)))
      .subscribe((token) => {
        this.downloadService.download(this.softwareService.downloadSoftware(token));
      });
  }

  public isReadOnly(): boolean {
    return !this.authService.isScopedWrite(this.softwareRepositoryName);
  }
}
