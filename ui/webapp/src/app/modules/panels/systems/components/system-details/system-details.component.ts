import { Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { InstanceDto } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { InstancesColumnsService } from 'src/app/modules/primary/instances/services/instances-columns.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { SystemsService } from 'src/app/modules/primary/systems/services/systems.service';
import { SystemsEditService } from '../../services/systems-edit.service';

import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import {
  BdNotificationCardComponent
} from '../../../../core/components/bd-notification-card/bd-notification-card.component';
import { MatIcon } from '@angular/material/icon';
import { BdIdentifierComponent } from '../../../../core/components/bd-identifier/bd-identifier.component';
import { BdExpandButtonComponent } from '../../../../core/components/bd-expand-button/bd-expand-button.component';
import { BdDataTableComponent } from '../../../../core/components/bd-data-table/bd-data-table.component';
import { BdPanelButtonComponent } from '../../../../core/components/bd-panel-button/bd-panel-button.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { MatTooltip } from '@angular/material/tooltip';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-system-details',
    templateUrl: './system-details.component.html',
  imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, BdNotificationCardComponent, MatIcon, BdIdentifierComponent, BdExpandButtonComponent, BdDataTableComponent, BdPanelButtonComponent, BdButtonComponent, MatTooltip, AsyncPipe]
})
export class SystemDetailsComponent implements OnInit, OnDestroy {
  private readonly systems = inject(SystemsService);
  private readonly areas = inject(NavAreasService);
  private readonly instances = inject(InstancesService);
  private readonly servers = inject(ServersService);
  private readonly instanceColumns = inject(InstancesColumnsService);
  protected readonly edit = inject(SystemsEditService);
  protected readonly auth = inject(AuthenticationService);

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
