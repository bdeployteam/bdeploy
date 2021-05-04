import { Component, OnInit, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';

@Component({
  selector: 'app-maintenance',
  templateUrl: './maintenance.component.html',
  styleUrls: ['./maintenance.component.css'],
})
export class MaintenanceComponent implements OnInit {
  /* template */ deleting$ = new BehaviorSubject<boolean>(false);

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

  constructor(private groups: GroupsService, private instances: InstancesService, private router: Router) {}

  ngOnInit(): void {}

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
            .subscribe((_) => {
              this.router.navigate(['instances', 'browser', this.groups.current$.value.name]);
            });
        }
      });
  }
}
