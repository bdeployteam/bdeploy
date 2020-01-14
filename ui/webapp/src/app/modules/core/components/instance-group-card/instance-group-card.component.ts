import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { cloneDeep } from 'lodash';
import { finalize } from 'rxjs/operators';
import { MessageBoxMode } from 'src/app/modules/shared/components/messagebox/messagebox.component';
import { MessageboxService } from 'src/app/modules/shared/services/messagebox.service';
import { EMPTY_INSTANCE_GROUP } from '../../../../models/consts';
import { InstanceGroupConfiguration, MinionMode } from '../../../../models/gen.dtos';
import { InstanceGroupDeleteDialogComponent } from '../../../instance-group/components/instance-group-delete-dialog/instance-group-delete-dialog.component';
import { InstanceGroupService } from '../../../instance-group/services/instance-group.service';
import { ConfigService } from '../../services/config.service';
import { LoggingService } from '../../services/logging.service';

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
  @Input() mode: MinionMode;
  @Output() removeEvent = new EventEmitter<boolean>();

  public currentGroup = cloneDeep(EMPTY_INSTANCE_GROUP);
  private loading = false;

  constructor(
    private loggingService: LoggingService,
    private instanceGroupService: InstanceGroupService,
    private dialog: MatDialog,
    private config: ConfigService,
    private router: Router,
    private mb: MessageboxService) { }

  ngOnInit() {
    if (this.instanceGroup === undefined) {
      // either of the two inputs must be set.
      if (this.instanceGroupId === undefined) {
        this.log.error('Neither instance group nor instance group id set');
        return;
      }

      this.loading = true;

      // tell the user we're loading this group
      this.currentGroup.name = this.instanceGroupId;
      this.currentGroup.description = 'Loading...';

      // load the group and set once available.
      this.instanceGroupService.getInstanceGroup(this.instanceGroupId).pipe(finalize(() => this.loading = false)).subscribe(value => {
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

  isModeOk() {
    if (this.currentGroup.managed && this.mode !== MinionMode.STANDALONE) {
      return true;
    } else if (!this.currentGroup.managed && this.mode === MinionMode.STANDALONE) {
      return true;
    }
    return false;
  }

  onClick() {
    if (this.loading) {
      return;
    }
    if (!this.isModeOk()) {
      // need to convert mode to enable.
      if (this.currentGroup.managed) {
        // group is managed but we're no longer a managed server -> migrate
        this.mb.open({title: 'Migrate from Managed Instance Group', message: `The Instance Group <b>${this.currentGroup.name}</b> was previously managed by a central server. This server has been migrated to a standalone mode. You need to confirm that this Instance Group may be migrated to standalone.`, mode: MessageBoxMode.CONFIRM}).subscribe(r => {
          if (r) {
            this.currentGroup.managed = false;
            this.instanceGroupService.updateInstanceGroup(this.currentGroup.name, this.currentGroup).subscribe(r => {
              this.router.navigate(['/instance/browser', this.currentGroup.name]);
            });
          }
        });
      } else {
        // group is not managed but we're no longer a standalone server -> need to attach!
        this.mb.open({title: 'Migrate from Standalone Instance Group', message: `The Instance Group <b>${this.currentGroup.name}</b> was created in standalone mode. This server has been migrated to a managed mode. You need to attach this Instance Group to an Instance Group <b>with the exact same name</b> on a Central Server to continue using it.`, mode: MessageBoxMode.CONFIRM}).subscribe(r => {
          if (r) {
            this.router.navigate(['/servers/attach/central']);
          }
        });
      }
    } else {
      this.router.navigate(['/instance/browser', this.currentGroup.name]);
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
