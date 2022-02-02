import { Component, OnInit, ViewChild } from '@angular/core';
import { NgForm } from '@angular/forms';
import { cloneDeep } from 'lodash-es';
import { combineLatest, debounceTime, Observable, of, Subscription, tap } from 'rxjs';
import {
  InstanceNodeConfiguration,
  ProcessControlGroupConfiguration,
  ProcessControlGroupHandlingType,
  ProcessControlGroupWaitType,
} from 'src/app/models/gen.dtos';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { isDirty } from 'src/app/modules/core/utils/dirty.utils';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';

const GROUP_TEMPLATE = {
  name: '',
  processOrder: [],
  startType: ProcessControlGroupHandlingType.PARALLEL,
  startWait: ProcessControlGroupWaitType.CONTINUE,
  stopType: ProcessControlGroupHandlingType.SEQUENTIAL,
};

@Component({
  selector: 'app-add-control-group',
  templateUrl: './add-control-group.component.html',
  styleUrls: ['./add-control-group.component.css'],
})
export class AddControlGroupComponent implements OnInit {
  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;
  @ViewChild('form') public form: NgForm;

  private subscription: Subscription;

  /* template */ handlingTypeValues = [ProcessControlGroupHandlingType.SEQUENTIAL, ProcessControlGroupHandlingType.PARALLEL];
  /* template */ waitTypeValues = [ProcessControlGroupWaitType.CONTINUE, ProcessControlGroupWaitType.WAIT];

  /* template */ newGroup: ProcessControlGroupConfiguration = cloneDeep(GROUP_TEMPLATE);
  /* template */ node: InstanceNodeConfiguration;
  /* template */ nodeName: string;
  /* template */ hasPendingChanges: boolean;

  constructor(public cfg: ConfigService, public edit: InstanceEditService, public servers: ServersService, private areas: NavAreasService) {}

  ngOnInit(): void {
    this.subscription = combineLatest([this.edit.state$, this.areas.panelRoute$]).subscribe(([state, route]) => {
      if (!state || !route || !route.params?.node) {
        this.node = null;
        return;
      }
      this.nodeName = route.params.node;
      this.node = state.config.nodeDtos.find((n) => n.nodeName === route.params.node)?.nodeConfiguration;
    });
  }

  ngAfterViewInit(): void {
    if (!this.form) {
      return;
    }
    this.subscription = this.form.valueChanges.pipe(debounceTime(100)).subscribe(() => {
      this.hasPendingChanges = this.isDirty();
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  public isDirty(): boolean {
    return isDirty(this.newGroup, GROUP_TEMPLATE);
  }

  /* template */ onSave() {
    this.doSave().subscribe((_) => this.tb.closePanel());
  }

  public doSave(): Observable<any> {
    this.node.controlGroups.push(this.newGroup);

    return of(true).pipe(
      tap((x) => {
        this.edit.conceal('Add Control Group ' + this.newGroup.name);
      })
    );
  }
}
