import { Component, ViewChild, inject } from '@angular/core';
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

@Component({
  selector: 'app-settings',
  templateUrl: './settings.component.html',
})
export class SettingsComponent {
  private actions = inject(ActionsService);
  private router = inject(Router);
  protected auth = inject(AuthenticationService);
  protected groups = inject(GroupsService);
  protected details = inject(GroupDetailsService);
  protected instances = inject(InstancesService);

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  private deleting$ = new BehaviorSubject<boolean>(false);
  private repairing$ = new BehaviorSubject<boolean>(false);

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
