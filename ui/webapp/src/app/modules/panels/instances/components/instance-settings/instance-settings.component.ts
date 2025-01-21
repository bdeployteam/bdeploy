import { Component, inject, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { Actions } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';

import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import {
  BdNotificationCardComponent
} from '../../../../core/components/bd-notification-card/bd-notification-card.component';
import { MatIcon } from '@angular/material/icon';
import { BdIdentifierComponent } from '../../../../core/components/bd-identifier/bd-identifier.component';
import { BdPanelButtonComponent } from '../../../../core/components/bd-panel-button/bd-panel-button.component';
import { MatTooltip } from '@angular/material/tooltip';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-instance-settings',
    templateUrl: './instance-settings.component.html',
  imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, BdNotificationCardComponent, MatIcon, BdIdentifierComponent, BdPanelButtonComponent, MatTooltip, BdButtonComponent, AsyncPipe]
})
export class InstanceSettingsComponent {
  private readonly groups = inject(GroupsService);
  private readonly instances = inject(InstancesService);
  private readonly router = inject(Router);
  private readonly actions = inject(ActionsService);
  protected readonly auth = inject(AuthenticationService);
  protected readonly edit = inject(InstanceEditService);
  protected readonly servers = inject(ServersService);

  private readonly deleting$ = new BehaviorSubject<boolean>(false);
  protected mappedDelete$ = this.actions.action([Actions.DELETE_INSTANCE], this.deleting$);

  @ViewChild(BdDialogComponent) private readonly dialog: BdDialogComponent;

  protected doDelete() {
    const inst = this.instances.current$.value;
    if (!inst) {
      return;
    }
    this.dialog
      .confirm(
        `Delete ${inst.instanceConfiguration.name}`,
        `Really delete the instance <strong>${inst.instanceConfiguration.name}</strong> with ID <strong>${inst.instanceConfiguration.id}</strong>? This cannot be undone and will delete all associated data files!`,
        'delete',
      )
      .subscribe((confirm) => {
        if (confirm) {
          this.deleting$.next(true);
          this.instances
            .delete(inst.instanceConfiguration.id)
            .pipe(finalize(() => this.deleting$.next(false)))
            .subscribe(() => {
              this.router.navigate(['instances', 'browser', this.groups.current$.value.name]);
            });
        }
      });
  }
}
