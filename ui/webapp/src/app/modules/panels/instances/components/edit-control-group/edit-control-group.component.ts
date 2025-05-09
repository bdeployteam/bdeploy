import { Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { cloneDeep } from 'lodash-es';
import { combineLatest, Observable, of, Subscription, tap } from 'rxjs';
import {
  InstanceNodeConfiguration,
  ProcessControlGroupConfiguration,
  ProcessControlGroupHandlingType,
  ProcessControlGroupWaitType
} from 'src/app/models/gen.dtos';
import {
  BdDialogToolbarComponent
} from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { isDirty } from 'src/app/modules/core/utils/dirty.utils';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { MatCard } from '@angular/material/card';


import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import {
  BdNotificationCardComponent
} from '../../../../core/components/bd-notification-card/bd-notification-card.component';
import { BdFormInputComponent } from '../../../../core/components/bd-form-input/bd-form-input.component';
import { TrimmedValidator } from '../../../../core/validators/trimmed.directive';
import { BdFormSelectComponent } from '../../../../core/components/bd-form-select/bd-form-select.component';
import { BdPopupDirective } from '../../../../core/components/bd-popup/bd-popup.directive';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { MatDivider } from '@angular/material/divider';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-edit-control-group',
    templateUrl: './edit-control-group.component.html',
  imports: [MatCard, BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, FormsModule, BdNotificationCardComponent, BdFormInputComponent, TrimmedValidator, BdFormSelectComponent, BdPopupDirective, BdButtonComponent, MatDivider, AsyncPipe]
})
export class EditControlGroupComponent implements OnInit, DirtyableDialog, OnDestroy {
  private readonly areas = inject(NavAreasService);
  protected readonly cfg = inject(ConfigService);
  protected readonly edit = inject(InstanceEditService);
  protected readonly servers = inject(ServersService);

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private readonly tb: BdDialogToolbarComponent;
  @ViewChild('form') public form: NgForm;

  private subscription?: Subscription;

  protected handlingTypeValues = [ProcessControlGroupHandlingType.SEQUENTIAL, ProcessControlGroupHandlingType.PARALLEL];
  protected waitTypeValues = [
    ProcessControlGroupWaitType.CONTINUE,
    ProcessControlGroupWaitType.WAIT,
    ProcessControlGroupWaitType.WAIT_UNTIL_STOPPED
  ];

  protected origGroup: ProcessControlGroupConfiguration;
  protected group: ProcessControlGroupConfiguration;
  protected node: InstanceNodeConfiguration;
  protected nodeName: string;
  protected hasPendingChanges: boolean;

  ngOnInit(): void {
    this.subscription = this.areas.registerDirtyable(this, 'panel');
    this.subscription.add(
      combineLatest([this.edit.state$, this.areas.panelRoute$]).subscribe(([state, route]) => {
        if (!state || !route?.params?.['node'] || !route.params['cgrp']) {
          this.node = null;
          return;
        }

        this.nodeName = route.params['node'];
        this.node = state.config.nodeDtos.find((n) => n.nodeName === route.params['node'])?.nodeConfiguration;

        const index = this.node.controlGroups.findIndex((cg) => cg.name === route.params['cgrp']);

        this.origGroup = this.node.controlGroups[index];
        this.group = cloneDeep(this.origGroup);
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  public isDirty(): boolean {
    return isDirty(this.group, this.origGroup);
  }

  canSave(): boolean {
    return this.form.valid;
  }

  protected onSave() {
    this.doSave().subscribe(() => {
      this.tb.closePanel();
      this.subscription?.unsubscribe();
    });
  }

  public doSave(): Observable<unknown> {
    const index = this.node.controlGroups.indexOf(this.origGroup);
    this.node.controlGroups[index] = this.group;

    return of(true).pipe(
      tap(() => {
        this.edit.conceal('Update Control Group ' + this.group.name);
      })
    );
  }

  protected onDelete() {
    this.removeGroup();
    // if this was the last group, we trigger creation of the default group.
    this.edit.getLastControlGroup(this.node);
    this.edit.conceal('Remove Control Group ' + this.origGroup.name);
    this.tb.closePanel();
  }

  public removeGroup() {
    const contained = this.group.processOrder;
    const apps = this.node.applications;
    for (const id of contained) {
      apps.splice(
        apps.findIndex((a) => a.id === id),
        1
      );
    }

    this.node.controlGroups.splice(
      this.node.controlGroups.findIndex((cg) => cg.name === this.origGroup.name),
      1
    );
  }
}
