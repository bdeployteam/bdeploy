import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { OperatingSystem } from 'src/app/models/gen.dtos';
import { SoftwarePackageGroup } from 'src/app/models/software.model';
import { SoftwareService } from '../../services/software.service';

@Component({
  selector: 'app-software-card',
  templateUrl: './software-card.component.html',
  styleUrls: ['./software-card.component.css']
})
export class SoftwareCardComponent implements OnInit {

  @Input() softwareRepositoryName: string;
  @Input() softwarePackageGroup: SoftwarePackageGroup;
  @Output() select = new EventEmitter();

  public diskUsage = '(...)';
  get operatingSystems(): OperatingSystem[] {return Array.from(this.softwarePackageGroup.osVersions.keys())};
  get versionsCount(): number {return Array.from(this.softwarePackageGroup.osVersions.values()).map(versions => versions.length).reduce((p,v) => p + v);};

  constructor(private softwareService: SoftwareService) { }

  ngOnInit() {
    this.softwareService.getSoftwareDiskUsage(this.softwareRepositoryName, this.softwarePackageGroup.name).subscribe(diskUsage => {
      this.diskUsage = diskUsage;
    });
  }

  public clickIt(): void {
    this.select.emit();
  }
}
