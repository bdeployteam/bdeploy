import { Component, Input, OnInit } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { InstanceGroupService } from '../../../../services/instance-group.service';
import { ErrorMessage, Logger, LoggingService } from '../../../core/services/logging.service';

@Component({
  selector: 'app-instance-group-logo',
  templateUrl: './instance-group-logo.component.html',
  styleUrls: ['./instance-group-logo.component.css']
})
export class InstanceGroupLogoComponent implements OnInit {

  log: Logger = this.loggingService.getLogger('InstanceGroupLogoComponent');

  private LOADING_IMG = this.sanitizer.bypassSecurityTrustStyle('url("/assets/loading.svg")');
  private NO_IMG = this.sanitizer.bypassSecurityTrustStyle('url("/assets/no-image.svg")');
  public imageUrl = this.LOADING_IMG;

  @Input()
  instanceGroupName: string;

  constructor(
    private loggingService: LoggingService,
    private instanceGroupService: InstanceGroupService,
    private sanitizer: DomSanitizer) { }

  ngOnInit() {
    if (this.instanceGroupName) {
      this.instanceGroupService.getInstanceGroup(this.instanceGroupName).subscribe(
        instanceGroup => {
          if (instanceGroup.logo) {
            this.setImageUrl(instanceGroup.name, instanceGroup.logo.id);
          } else {
            this.imageUrl = this.NO_IMG;
          }
        },
        error => {
          this.log.error(new ErrorMessage('reading instance group failed', error));
        }
      );
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
