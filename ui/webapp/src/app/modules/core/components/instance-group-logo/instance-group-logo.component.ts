import { Component, Input, OnChanges } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { InstanceGroupConfiguration } from 'src/app/models/gen.dtos';
import { InstanceGroupService } from '../../../instance-group/services/instance-group.service';
import { Logger, LoggingService } from '../../services/logging.service';

@Component({
  selector: 'app-instance-group-logo',
  templateUrl: './instance-group-logo.component.html',
  styleUrls: ['./instance-group-logo.component.css'],
})
export class InstanceGroupLogoComponent implements OnChanges {
  log: Logger = this.loggingService.getLogger('InstanceGroupLogoComponent');

  private LOADING_IMG = this.sanitizer.bypassSecurityTrustStyle('url("/assets/loading.svg")');
  private NO_IMG = this.sanitizer.bypassSecurityTrustStyle('url("/assets/no-image.svg")');
  public imageUrl = this.LOADING_IMG;

  @Input()
  instanceGroup: InstanceGroupConfiguration;

  constructor(
    private loggingService: LoggingService,
    private instanceGroupService: InstanceGroupService,
    private sanitizer: DomSanitizer
  ) {}

  ngOnChanges() {
    if (!this.instanceGroup) {
      return;
    }
    if (this.instanceGroup.logo) {
      this.setImageUrl(this.instanceGroup.name, this.instanceGroup.logo.id);
    } else {
      this.imageUrl = this.NO_IMG;
    }
  }

  private setImageUrl(name: string, id: string): void {
    this.imageUrl = this.sanitizer.bypassSecurityTrustStyle(
      'url("' + this.instanceGroupService.getInstanceGroupImageUrl(name) + '?logo=' + id + '")'
    );
  }
}
