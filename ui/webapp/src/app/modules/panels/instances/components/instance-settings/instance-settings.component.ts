import { Component, ViewChild, inject } from '@angular/core';
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

@Component({
  selector: 'app-instance-settings',
  templateUrl: './instance-settings.component.html',
})
export class InstanceSettingsComponent {
  private readonly groups = inject(GroupsService);
  private readonly instances = inject(InstancesService);
  private readonly router = inject(Router);
  private readonly actions = inject(ActionsService);
  protected readonly auth = inject(AuthenticationService);
  protected readonly edit = inject(InstanceEditService);

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
