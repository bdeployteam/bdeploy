import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { finalize } from 'rxjs/operators';
import { ManifestKey } from '../models/gen.dtos';
import { LoggingService } from '../services/logging.service';
import { SoftwareService } from '../services/software.service';

@Component({
  selector: 'app-software-list',
  templateUrl: './software-list.component.html',
  styleUrls: ['./software-list.component.css']
})
export class SoftwareListComponent implements OnInit {

  private log = this.loggingService.getLogger('SoftwareListComponent');

  @Input() softwareRepositoryName: string;
  @Input() softwareVersions: ManifestKey[];
  @Output() public deleted = new EventEmitter();

  public exporting: ManifestKey = null;

  constructor(private softwareService: SoftwareService, private loggingService: LoggingService) { }

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
        window.location.href = this.softwareService.downloadSoftware(this.softwareRepositoryName, token);
      });
  }

}
