import { Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { BehaviorSubject, Subscription, combineLatest } from 'rxjs';
import { InstanceDto } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
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
export class SystemDetailsComponent implements OnInit, OnDestroy {
  private readonly systems = inject(SystemsService);
  private readonly areas = inject(NavAreasService);
  private readonly instances = inject(InstancesService);
  private readonly servers = inject(ServersService);
  private readonly instanceColumns = inject(InstancesColumnsService);
  protected edit = inject(SystemsEditService);
  protected auth = inject(AuthenticationService);

  protected loading$ = new BehaviorSubject<boolean>(true);
  protected instancesUsing$ = new BehaviorSubject<InstanceDto[]>(null);
  protected instancesUsingColumns = [this.instanceColumns.instanceNameColumn, this.instanceColumns.instanceIdColumn];

  protected getRecordRoute = (row: InstanceDto) => [
    '/instances',
    'dashboard',
    this.areas.groupContext$.value,
    row.instanceConfiguration.id,
  ];
  private subscription: Subscription;

  @ViewChild(BdDialogComponent) private readonly dialog: BdDialogComponent;

  ngOnInit() {
    this.subscription = combineLatest([this.edit.current$, this.instances.instances$]).subscribe(([c, i]) => {
      if (!c || !i) {
        this.instancesUsing$.next(null);
        return;
      }

      this.instancesUsing$.next(i.filter((instance) => instance.instanceConfiguration?.system?.name === c.key?.name));
      this.loading$.next(false);
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected isSynchronized(): boolean {
    return this.servers.isSynchronized(
      this.edit.current$.value?.minion
        ? this.servers.servers$.value?.find((s) => s.hostName === this.edit.current$.value?.minion)
        : null,
    );
  }

  protected onDelete() {
    this.dialog
      .confirm(
        `Delete ${this.edit.current$.value?.config?.name}`,
        `Are you sure you want to delete ${this.edit.current$.value?.config?.name}?`,
      )
      .subscribe((confirmed) => {
        if (confirmed) {
          this.systems.delete(this.edit.current$.value?.config.id).subscribe(() => {
            this.areas.closePanel();
          });
        }
      });
  }
}
