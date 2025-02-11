import { Component, inject, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { Actions, InstanceGroupConfiguration } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ActionsService } from 'src/app/modules/core/services/actions.service';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { GroupDetailsService } from '../../services/group-details.service';

import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { MatIcon } from '@angular/material/icon';
import { BdIdentifierComponent } from '../../../../core/components/bd-identifier/bd-identifier.component';
import { BdPanelButtonComponent } from '../../../../core/components/bd-panel-button/bd-panel-button.component';
import { MatDivider } from '@angular/material/divider';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-settings',
    templateUrl: './settings.component.html',
  imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, MatIcon, BdIdentifierComponent, BdPanelButtonComponent, MatDivider, BdButtonComponent, AsyncPipe]
})
export class SettingsComponent {
  private readonly actions = inject(ActionsService);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthenticationService);
  protected readonly groups = inject(GroupsService);
  protected readonly details = inject(GroupDetailsService);
  protected readonly instances = inject(InstancesService);

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  private readonly deleting$ = new BehaviorSubject<boolean>(false);
  private readonly repairing$ = new BehaviorSubject<boolean>(false);

  protected mappedDelete$ = this.actions.action([Actions.DELETE_GROUP], this.deleting$);
  protected mappedRepair$ = this.actions.action([Actions.FSCK_BHIVE, Actions.PRUNE_BHIVE], this.repairing$);

  protected onRepairAndPrune(group: InstanceGroupConfiguration): void {
    this.dialog
      .confirm('Repair and Prune', 'Repairing will remove any (anyhow) damaged and unusable elements from the BHive')
      .subscribe((confirmed) => {
        if (confirmed) {
          this.repairing$.next(true);
          this.details
            .repairAndPrune(group.name)
            .pipe(finalize(() => this.repairing$.next(false)))
            .subscribe(({ repaired, pruned }) => {
              console.groupCollapsed('Damaged Objects');
              const keys = Object.keys(repaired);
              for (const key of keys) {
                console.log(key, ':', repaired[key]);
              }
              console.groupEnd();

              const repairMessage = keys?.length
                ? `Repair removed ${keys.length} damaged objects`
                : `No damaged objects were found.`;

              const pruneMessage = `Prune freed <strong>${pruned}</strong> in ${group.name}.`;
              this.dialog.info(`Repair and Prune`, `${repairMessage}<br/>${pruneMessage}`, 'build').subscribe();
            });
        }
      });
  }

  protected onDelete(group: InstanceGroupConfiguration): void {
    this.dialog
      .confirm(
        `Delete ${group.name}`,
        `Are you sure you want to delete the instance group <strong>${group.title}</strong>? ` +
          `This will permanently delete any associated instances (currently: ${this.instances.instances$.value.length}).`,
        'delete',
        group.name,
        `Confirm using Instance Group ID`,
      )
      .subscribe((r) => {
        if (r) {
          this.deleting$.next(true);
          this.details
            .delete(group)
            .pipe(finalize(() => this.deleting$.next(false)))
            .subscribe(() => {
              this.router.navigate(['groups', 'browser']);
            });
        }
      });
  }
}
