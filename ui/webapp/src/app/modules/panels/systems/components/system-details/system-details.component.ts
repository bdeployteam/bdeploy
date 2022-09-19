import { Component, OnDestroy, ViewChild } from '@angular/core';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { InstanceDto } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstancesColumnsService } from 'src/app/modules/primary/instances/services/instances-columns.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { SystemsService } from 'src/app/modules/primary/systems/services/systems.service';
import { SystemsEditService } from '../../services/systems-edit.service';

@Component({
  selector: 'app-system-details',
  templateUrl: './system-details.component.html',
})
export class SystemDetailsComponent implements OnDestroy {
  /* template */ loading$ = new BehaviorSubject<boolean>(true);
  /* template */ instancesUsing$ = new BehaviorSubject<InstanceDto[]>(null);
  /* template */ instancesUsingColumns = [
    this.instanceColumns.instanceNameColumn,
    this.instanceColumns.instanceIdColumn,
  ];

  /* template */ getRecordRoute = (row: InstanceDto) => {
    return [
      '/instances',
      'dashboard',
      this.areas.groupContext$.value,
      row.instanceConfiguration.id,
    ];
  };
  private subscription: Subscription;

  @ViewChild(BdDialogComponent) private dialog: BdDialogComponent;

  constructor(
    private systems: SystemsService,
    public edit: SystemsEditService,
    private areas: NavAreasService,
    instances: InstancesService,
    private servers: ServersService,
    private instanceColumns: InstancesColumnsService
  ) {
    this.subscription = combineLatest([
      edit.current$,
      instances.instances$,
    ]).subscribe(([c, i]) => {
      if (!c || !i) {
        this.instancesUsing$.next(null);
        return;
      }

      this.instancesUsing$.next(
        i.filter((i) => i.instanceConfiguration?.system?.name === c.key?.name)
      );

      this.loading$.next(false);
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ isSynchronized(): boolean {
    return this.servers.isSynchronized(
      this.edit.current$.value?.minion
        ? this.servers.servers$.value?.find(
            (s) => s.hostName === this.edit.current$.value?.minion
          )
        : null
    );
  }

  /* template */ onDelete() {
    this.dialog
      .confirm(
        `Delete ${this.edit.current$.value?.config?.name}`,
        `Are you sure you want to delete ${this.edit.current$.value?.config?.name}?`
      )
      .subscribe((confirmed) => {
        if (confirmed) {
          this.systems
            .delete(this.edit.current$.value?.config.id)
            .subscribe(() => {
              this.areas.closePanel();
            });
        }
      });
  }
}
