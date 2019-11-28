import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ManifestKey } from '../../../../models/gen.dtos';
import { SoftwareService } from '../../services/software.service';

@Component({
  selector: 'app-software-card',
  templateUrl: './software-card.component.html',
  styleUrls: ['./software-card.component.css']
})
export class SoftwareCardComponent implements OnInit {

  @Input() softwareRepositoryName: string;
  @Input() softwarePackageName: string;
  @Input() softwarePackageVersions: ManifestKey[];
  @Output() select = new EventEmitter();

  public diskUsage = '(...)';

  constructor(private softwareService: SoftwareService) { }

  ngOnInit() {
    this.softwareService.getSoftwareDiskUsage(this.softwareRepositoryName, this.softwarePackageVersions[0]).subscribe(diskUsage => {
      this.diskUsage = diskUsage;
    });
  }

  public clickIt(): void {
    this.select.emit();
  }
}
