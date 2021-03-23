import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { ManagedServersService } from 'src/app/modules/legacy/servers/services/managed-servers.service';
import { MessageBoxMode } from 'src/app/modules/legacy/shared/components/messagebox/messagebox.component';
import { MessageboxService } from 'src/app/modules/legacy/shared/services/messagebox.service';
import { InstanceGroupConfiguration, MinionMode } from '../../../../../models/gen.dtos';
import { AuthenticationService } from '../../../../core/services/authentication.service';
import { ConfigService } from '../../../../core/services/config.service';
import { LoggingService } from '../../../../core/services/logging.service';
import { InstanceGroupDeleteDialogComponent } from '../../../instance-group/components/instance-group-delete-dialog/instance-group-delete-dialog.component';
import { InstanceGroupService } from '../../../instance-group/services/instance-group.service';

@Component({
  selector: 'app-instance-group-card',
  templateUrl: './instance-group-card.component.html',
  styleUrls: ['./instance-group-card.component.css'],
})
export class InstanceGroupCardComponent implements OnInit {
  private log = this.loggingService.getLogger('InstanceGroupCardComponent');

  @Input() instanceGroup: InstanceGroupConfiguration;
  @Input() isAttachAllowed: boolean;
  @Output() removeEvent = new EventEmitter<boolean>();

  private loading = false;

  constructor(
    private loggingService: LoggingService,
    public authService: AuthenticationService,
    private instanceGroupService: InstanceGroupService,
    private dialog: MatDialog,
    private config: ConfigService,
    private router: Router,
    private mb: MessageboxService,
    private managedServers: ManagedServersService
  ) {}

  ngOnInit() {}

  isModeOk() {
    return (
      (this.instanceGroup.managed && this.config.config.mode !== MinionMode.STANDALONE) ||
      (!this.instanceGroup.managed && this.config.config.mode === MinionMode.STANDALONE)
    );
  }

  onClick() {
    if (this.loading) {
      return;
    }
    if (!this.isModeOk()) {
      // need to convert mode to enable.
      if (this.instanceGroup.managed) {
        // group is managed but we're no longer a managed server -> migrate
        this.mb
          .open({
            title: 'Migrate from Managed Instance Group',
            message: `The Instance Group <strong>${this.instanceGroup.name}</strong> was previously managed by a central server. This server has been migrated to a standalone mode. You need to confirm that this Instance Group may be migrated to standalone.`,
            mode: MessageBoxMode.CONFIRM,
          })
          .subscribe((r) => {
            if (r) {
              this.instanceGroup.managed = false;
              this.instanceGroupService.updateInstanceGroup(this.instanceGroup.name, this.instanceGroup).subscribe((_) => {
                this.router.navigate(['/l/instance/browser', this.instanceGroup.name]);
              });
            }
          });
      } else {
        // group is not managed but we're no longer a standalone server -> need to attach!
        this.mb
          .open({
            title: 'Migrate from Standalone Instance Group',
            message: `The Instance Group <strong>${this.instanceGroup.name}</strong> was created in standalone mode. This server has been migrated to a managed mode. You need to attach this Instance Group to an Instance Group <strong>with the exact same name</strong> on a Central Server to continue using it.`,
            mode: MessageBoxMode.CONFIRM,
          })
          .subscribe((r) => {
            if (r) {
              this.initiateMigration();
            }
          });
      }
    } else {
      this.router.navigate(['/l/instance/browser', this.instanceGroup.name]);
    }
  }

  async initiateMigration() {
    const needMigration = await this.managedServers.isDataMigrationRequired(this.instanceGroup.name).toPromise();

    if (needMigration) {
      const r = await this.mb.openAsync({
        title: 'Data Migration',
        message: `The Instance Group <strong>${this.instanceGroup.name}</strong> contains instance data which needs to be migrated. This migration will <strong>discard</strong> old instance versions and create a new, compatible configuration from the <strong>latest</strong> instance version.`,
        mode: MessageBoxMode.CONFIRM_WARNING,
      });
      if (r) {
        await this.managedServers.performDataMigration(this.instanceGroup.name).toPromise();
      } else {
        return;
      }
    }

    this.router.navigate(['/l/servers/attach/central']);
  }

  delete(): void {
    const dialogRef = this.dialog.open(InstanceGroupDeleteDialogComponent, {
      minWidth: '300px',
      maxWidth: '90%',
      data: {
        name: this.instanceGroup.name,
        title: this.instanceGroup.title,
        confirmation: '',
      },
    });

    dialogRef.afterClosed().subscribe((result) => {
      if (result === false || result === undefined) {
        return;
      }
      if (this.instanceGroup.name === result) {
        this.instanceGroupService.deleteInstanceGroup(result).subscribe((r) => {
          this.removeEvent.emit(true);
        });
      } else {
        this.log.warn('Instance group ID does not match');
      }
    });
  }

  isManagedServer() {
    return this.config.config.mode === MinionMode.MANAGED;
  }
}
