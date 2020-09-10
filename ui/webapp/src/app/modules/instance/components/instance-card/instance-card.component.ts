import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { InstanceGroupService } from 'src/app/modules/instance-group/services/instance-group.service';
import { MessageboxService } from 'src/app/modules/shared/services/messagebox.service';
import { InstanceDto, InstanceGroupConfiguration } from '../../../../models/gen.dtos';
import { LoggingService } from '../../../core/services/logging.service';
import { MessageBoxMode } from '../../../shared/components/messagebox/messagebox.component';
import { InstanceService } from '../../services/instance.service';
import { InstanceBannerEditComponent } from '../instance-banner-edit/instance-banner-edit.component';


@Component({
  selector: 'app-instance-card',
  templateUrl: './instance-card.component.html',
  styleUrls: ['./instance-card.component.css']
})
export class InstanceCardComponent implements OnInit {

  @Input() instanceDto: InstanceDto;
  @Input() instanceGroupName: string;
  @Output() removeEvent = new EventEmitter<boolean>();

  instanceGroup: InstanceGroupConfiguration;

  private log = this.loggingService.getLogger('InstanceCardComponent');

  constructor(
    public authService: AuthenticationService,
    private loggingService: LoggingService,
    private instanceService: InstanceService,
    private mbService: MessageboxService,
    private instanceGroupService: InstanceGroupService,
    private dialog: MatDialog,
  ) {}

  ngOnInit() {
    this.instanceGroupService.getInstanceGroup(this.instanceGroupName).subscribe(r => this.instanceGroup = r);
  }

  onConfigureBanner() {
    this.instanceService.getInstanceBanner(this.instanceGroupName, this.instanceDto.instanceConfiguration.uuid).subscribe(banner => {
      this.dialog.open(InstanceBannerEditComponent, {
        width: '600px',
        data: {
          instanceBanner: banner
        },
      }).afterClosed().subscribe(r => {
        if (r) {
          this.instanceService.updateInstanceBanner(this.instanceGroupName, this.instanceDto.instanceConfiguration.uuid, r).subscribe(
            // nothing to update
          );
        }
      });
    });
}

  delete(): void {
    this.mbService
      .open({
        title: 'Delete Instance ' + this.instanceDto.instanceConfiguration.name,
        message: 'Deleting an instance <strong>cannot be undone</strong>.',
        mode: MessageBoxMode.CONFIRM_WARNING
      })
      .subscribe(result => {
        if (result !== true) {
          return;
        }
        this.instanceService
          .deleteInstance(this.instanceGroupName, this.instanceDto.instanceConfiguration.uuid)
          .subscribe(
            r => {
              this.removeEvent.emit(true);
            }
          );
      });
  }
}
