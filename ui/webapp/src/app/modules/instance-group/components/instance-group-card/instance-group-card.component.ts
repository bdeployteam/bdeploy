import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { cloneDeep } from 'lodash';
import { EMPTY_INSTANCE_GROUP } from '../../../../models/consts';
import { InstanceGroupConfiguration, MinionMode } from '../../../../models/gen.dtos';
import { ConfigService } from '../../../core/services/config.service';
import { LoggingService } from '../../../core/services/logging.service';
import { InstanceGroupService } from '../../services/instance-group.service';
import { InstanceGroupDeleteDialogComponent } from '../instance-group-delete-dialog/instance-group-delete-dialog.component';

@Component({
  selector: 'app-instance-group-card',
  templateUrl: './instance-group-card.component.html',
  styleUrls: ['./instance-group-card.component.css']
})
export class InstanceGroupCardComponent implements OnInit {

  private log = this.loggingService.getLogger('InstanceGroupCardComponent');

  @Input() instanceGroup: InstanceGroupConfiguration;
  @Input() instanceGroupId: string;
  @Input() isAttachAllowed: boolean;
  @Output() removeEvent = new EventEmitter<boolean>();

  public currentGroup = cloneDeep(EMPTY_INSTANCE_GROUP);

  constructor(
    private loggingService: LoggingService,
    private instanceGroupService: InstanceGroupService,
    private dialog: MatDialog,
    private config: ConfigService) { }

  ngOnInit() {
    if (this.instanceGroup === undefined) {
      // either of the two inputs must be set.
      if (this.instanceGroupId === undefined) {
        this.log.error('Neither instance group nor instance group id set');
        return;
      }

      // tell the user we're loading this group
      this.currentGroup.name = this.instanceGroupId;
      this.currentGroup.description = 'Loading...';

      // load the group and set once available.
      this.instanceGroupService.getInstanceGroup(this.instanceGroupId).subscribe(value => {
        this.currentGroup = value;
      }, error => {
        this.log.warn(`Cannot load instance group ${this.instanceGroupId}`);
        this.removeEvent.emit(true);
      });
    } else {
      // use the group passed from the outside.
      this.currentGroup = this.instanceGroup;
    }
  }

  delete(): void {
    const dialogRef = this.dialog.open(InstanceGroupDeleteDialogComponent, {
      minWidth: '300px',
      maxWidth: '90%',
      data: { name: this.currentGroup.name, confirmation: '' }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result === false || result === undefined) {
        return;
      }
      if (this.currentGroup.name === result) {
        this.instanceGroupService.deleteInstanceGroup(result).subscribe(r => {
          this.removeEvent.emit(true);
        });
      } else {
        this.log.warn('Instance group name does not match');
      }
    });
  }

  isManagedServer() {
    return this.config.config.mode === MinionMode.MANAGED;
  }

}
