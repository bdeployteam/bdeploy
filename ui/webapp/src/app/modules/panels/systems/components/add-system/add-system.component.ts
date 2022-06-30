import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { NgForm } from '@angular/forms';
import { BehaviorSubject, finalize, Observable, Subscription } from 'rxjs';
import { ManagedMasterDto, SystemConfiguration } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { SystemsService } from 'src/app/modules/primary/systems/services/systems.service';

@Component({
  selector: 'app-add-system',
  templateUrl: './add-system.component.html',
})
export class AddSystemComponent implements OnInit, OnDestroy, DirtyableDialog {
  /* template */ loading$ = new BehaviorSubject<boolean>(false);
  /* template */ saving$ = new BehaviorSubject<boolean>(false);
  /* template */ system: Partial<SystemConfiguration> = {};
  /* template */ isCentral = false;
  /* template */ server: ManagedMasterDto;
  /* template */ serverList: ManagedMasterDto[] = [];
  /* template */ serverNames: string[] = [];

  private subscription: Subscription;

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;
  @ViewChild('form') public form: NgForm;

  constructor(
    private areas: NavAreasService,
    private systems: SystemsService,
    public servers: ServersService,
    private cfg: ConfigService,
    private groups: GroupsService
  ) {
    this.subscription = areas.registerDirtyable(this, 'panel');
  }

  ngOnInit(): void {
    this.subscription.add(
      this.cfg.isCentral$.subscribe((value) => {
        this.isCentral = value;
      })
    );

    this.groups
      .newUuid()
      .pipe(finalize(() => this.loading$.next(false)))
      .subscribe((r) => {
        this.system.uuid = r;
      });

    this.subscription.add(
      this.servers.servers$.subscribe((s) => {
        this.serverList = s;
        this.serverNames = this.serverList.map(
          (c) => `${c.hostName} - ${c.description}`
        );
      })
    );
  }

  isDirty(): boolean {
    return this.form.dirty;
  }

  canSave(): boolean {
    return this.form.valid;
  }

  /* template */ onSave() {
    this.saving$.next(true);
    this.doSave().subscribe(() => {
      this.reset();
    });
  }

  private reset() {
    this.areas.closePanel();
    this.subscription.unsubscribe();
  }

  public doSave(): Observable<void> {
    this.saving$.next(true);
    return this.systems
      .create({
        config: this.system as SystemConfiguration,
        minion: this.server?.hostName,
        key: null,
      })
      .pipe(
        finalize(() => {
          this.saving$.next(false);
        })
      );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
