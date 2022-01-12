import { Component, OnInit, ViewChild } from '@angular/core';
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
  styleUrls: ['./settings.component.css'],
})
export class SettingsComponent implements OnInit {
  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  /* template */ deleting$ = new BehaviorSubject<boolean>(false);
  /* template */ pruning$ = new BehaviorSubject<boolean>(false);
  /* template */ repairing$ = new BehaviorSubject<boolean>(false);

  constructor(
    public auth: AuthenticationService,
    public groups: GroupsService,
    public details: GroupDetailsService,
    public instances: InstancesService,
    private router: Router
  ) {}

  ngOnInit(): void {}

  /* template */ onRepair(group: InstanceGroupConfiguration): void {
    this.dialog.confirm('Repair', 'Repairing will remove any (anyhow) damaged and unusable elements from the BHive').subscribe((confirmed) => {
      if (confirmed) {
        this.repairing$.next(true);
        this.details
          .repair(group.name)
          .pipe(finalize(() => this.repairing$.next(false)))
          .subscribe((r) => {
            console.groupCollapsed('Damaged Objects');
            const keys = Object.keys(r);
            for (const key of keys) {
              console.log(key, ':', r[key]);
            }
            console.groupEnd();

            this.dialog
              .info(`Repair`, !!keys?.length ? `Repair removed ${keys.length} damaged objects` : `No damaged objects were found.`, 'build')
              .subscribe();
          });
      }
    });
  }

  /* template */ onPrune(group: InstanceGroupConfiguration): void {
    this.pruning$.next(true);
    this.details
      .prune(group.name)
      .pipe(finalize(() => this.pruning$.next(false)))
      .subscribe((r) => {
        this.dialog.info('Prune', `Prune freed <strong>${r}</strong> in ${group.name}.`).subscribe();
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
            .subscribe((r) => {
              this.router.navigate(['groups', 'browser']);
            });
        }
      });
  }
}
