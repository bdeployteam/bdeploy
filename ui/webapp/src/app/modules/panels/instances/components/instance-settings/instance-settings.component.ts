import { Component, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';

@Component({
  selector: 'app-instance-settings',
  templateUrl: './instance-settings.component.html',
})
export class InstanceSettingsComponent {
  /* template */ deleting$ = new BehaviorSubject<boolean>(false);

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

  constructor(
    public auth: AuthenticationService,
    public edit: InstanceEditService,
    private groups: GroupsService,
    private instances: InstancesService,
    private router: Router
  ) {}

  /* template */ doDelete() {
    const inst = this.instances.current$.value;
    if (!inst) {
      return;
    }
    this.dialog
      .confirm(
        `Delete ${inst.instanceConfiguration.name}`,
        `Really delete the instance <strong>${inst.instanceConfiguration.name}</strong> with ID <strong>${inst.instanceConfiguration.uuid}</strong>? This cannot be undone and will delete all associated data files!`,
        'delete'
      )
      .subscribe((confirm) => {
        if (confirm) {
          this.deleting$.next(true);
          this.instances
            .delete(inst.instanceConfiguration.uuid)
            .pipe(finalize(() => this.deleting$.next(false)))
            .subscribe(() => {
              this.router.navigate([
                'instances',
                'browser',
                this.groups.current$.value.name,
              ]);
            });
        }
      });
  }
}
