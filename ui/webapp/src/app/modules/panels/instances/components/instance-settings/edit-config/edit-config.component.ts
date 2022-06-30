import { AfterViewInit, Component, OnDestroy, ViewChild } from '@angular/core';
import { NgForm } from '@angular/forms';
import { combineLatest, Observable, of, Subscription } from 'rxjs';
import { debounceTime, tap } from 'rxjs/operators';
import { InstancePurpose, ManifestKey } from 'src/app/models/gen.dtos';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import {
  GlobalEditState,
  InstanceEditService,
} from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { SystemsService } from 'src/app/modules/primary/systems/services/systems.service';

@Component({
  selector: 'app-edit-config',
  templateUrl: './edit-config.component.html',
})
export class EditConfigComponent
  implements OnDestroy, DirtyableDialog, AfterViewInit
{
  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;
  @ViewChild('form') public form: NgForm;

  private subscription: Subscription;

  /* template */ purposes = [
    InstancePurpose.PRODUCTIVE,
    InstancePurpose.DEVELOPMENT,
    InstancePurpose.TEST,
  ];
  /* template */ hasPendingChanges: boolean;

  /* template */ systemKeys: ManifestKey[];
  /* template */ systemLabels: string[];
  /* template */ systemSel: ManifestKey;

  constructor(
    public cfg: ConfigService,
    public edit: InstanceEditService,
    public servers: ServersService,
    public systems: SystemsService,
    areas: NavAreasService
  ) {
    this.subscription = areas.registerDirtyable(this, 'panel');
    this.subscription.add(
      combineLatest([systems.systems$, edit.state$]).subscribe(([s, i]) => {
        if (!s?.length || !i) {
          return;
        }
        this.systemKeys = s.map((s) => s.key);
        this.systemLabels = s.map(
          (s) => `${s.config.name} (${s.config.description})`
        );

        if (i.config.config.system) {
          this.systemSel = this.systemKeys.find(
            (k) => k.name === i.config.config.system.name
          );
        }
      })
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
    return this.edit.hasPendingChanges();
  }

  canSave(): boolean {
    return this.form.valid;
  }

  /* template */ onSave() {
    this.doSave().subscribe(() => this.tb.closePanel());
  }

  /* template */ onSystemChange(state: GlobalEditState, value: ManifestKey) {
    state.config.config.system = value;
  }

  public doSave(): Observable<any> {
    return of(true).pipe(
      tap(() => {
        this.edit.conceal('Change Instance Configuration');
      })
    );
  }
}
