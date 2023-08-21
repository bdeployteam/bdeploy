import { Component, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { InstanceGroupConfiguration } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { GroupDetailsService } from '../../services/group-details.service';

@Component({
  selector: 'app-settings',
  templateUrl: './settings.component.html',
})
export class SettingsComponent {
  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  /* template */ deleting$ = new BehaviorSubject<boolean>(false);
  /* template */ repairing$ = new BehaviorSubject<boolean>(false);

  constructor(
    public auth: AuthenticationService,
    public groups: GroupsService,
    public details: GroupDetailsService,
    public instances: InstancesService,
    private router: Router
  ) {}

  /* template */ onRepairAndPrune(group: InstanceGroupConfiguration): void {
    this.dialog
      .confirm(
        'Repair and Prune',
        'Repairing will remove any (anyhow) damaged and unusable elements from the BHive'
      )
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
              this.dialog
                .info(
                  `Repair and Prune`,
                  `${repairMessage}<br/>${pruneMessage}`,
                  'build'
                )
                .subscribe();
            });
        }
      });
  }

  /* template */ onDelete(group: InstanceGroupConfiguration): void {
    this.dialog
      .confirm(
        `Delete ${group.name}`,
        `Are you sure you want to delete the instance group <strong>${group.title}</strong>? ` +
          `This will permanently delete any associated instances (currently: ${this.instances.instances$.value.length}).`,
        'delete',
        group.name,
        `Confirm using Instance Group ID`
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
