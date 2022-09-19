import {
  AfterViewInit,
  Component,
  OnDestroy,
  OnInit,
  ViewChild,
} from '@angular/core';
import { NgForm } from '@angular/forms';
import { cloneDeep } from 'lodash-es';
import {
  combineLatest,
  debounceTime,
  Observable,
  of,
  Subscription,
  tap,
} from 'rxjs';
import {
  InstanceNodeConfiguration,
  ProcessControlGroupConfiguration,
  ProcessControlGroupHandlingType,
  ProcessControlGroupWaitType,
} from 'src/app/models/gen.dtos';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { isDirty } from 'src/app/modules/core/utils/dirty.utils';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';

@Component({
  selector: 'app-edit-control-group',
  templateUrl: './edit-control-group.component.html',
})
export class EditControlGroupComponent
  implements OnInit, DirtyableDialog, OnDestroy, AfterViewInit
{
  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;
  @ViewChild('form') public form: NgForm;

  private subscription: Subscription;

  /* template */ handlingTypeValues = [
    ProcessControlGroupHandlingType.SEQUENTIAL,
    ProcessControlGroupHandlingType.PARALLEL,
  ];
  /* template */ waitTypeValues = [
    ProcessControlGroupWaitType.CONTINUE,
    ProcessControlGroupWaitType.WAIT,
  ];

  /* template */ origGroup: ProcessControlGroupConfiguration;
  /* template */ group: ProcessControlGroupConfiguration;
  /* template */ node: InstanceNodeConfiguration;
  /* template */ nodeName: string;
  /* template */ hasPendingChanges: boolean;

  constructor(
    public cfg: ConfigService,
    public edit: InstanceEditService,
    public servers: ServersService,
    private areas: NavAreasService
  ) {
    this.subscription = areas.registerDirtyable(this, 'panel');
  }

  ngOnInit(): void {
    this.subscription.add(
      combineLatest([this.edit.state$, this.areas.panelRoute$]).subscribe(
        ([state, route]) => {
          if (!state || !route || !route.params?.node || !route.params?.cgrp) {
            this.node = null;
            return;
          }

          this.nodeName = route.params.node;
          this.node = state.config.nodeDtos.find(
            (n) => n.nodeName === route.params.node
          )?.nodeConfiguration;

          const index = this.node.controlGroups.findIndex(
            (cg) => cg.name === route.params.cgrp
          );

          this.origGroup = this.node.controlGroups[index];
          this.group = cloneDeep(this.origGroup);
        }
      )
    );
  }

  ngAfterViewInit(): void {
    if (!this.form) {
      return;
    }
    this.subscription.add(
      this.form.valueChanges.pipe(debounceTime(100)).subscribe(() => {
        this.hasPendingChanges = this.isDirty();
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  public isDirty(): boolean {
    return isDirty(this.group, this.origGroup);
  }

  canSave(): boolean {
    return this.form.valid;
  }

  /* template */ onSave() {
    this.doSave().subscribe(() => this.tb.closePanel());
  }

  public doSave(): Observable<any> {
    const index = this.node.controlGroups.indexOf(this.origGroup);
    this.node.controlGroups[index] = this.group;

    return of(true).pipe(
      tap(() => {
        this.edit.conceal('Update Control Group ' + this.group.name);
      })
    );
  }

  /* template */ onDelete() {
    this.removeGroup();
    // if this was the last group, we trigger creation of the default group.
    this.edit.getLastControlGroup(this.node);
    this.edit.conceal('Remove Control Group ' + this.group.name);
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
      this.node.controlGroups.findIndex((cg) => cg.name === this.group.name),
      1
    );
  }
}
