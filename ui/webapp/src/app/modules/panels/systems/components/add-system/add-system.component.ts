import { Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { NgForm } from '@angular/forms';
import { BehaviorSubject, Observable, Subscription, finalize } from 'rxjs';
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
  private readonly areas = inject(NavAreasService);
  private readonly systems = inject(SystemsService);
  private readonly cfg = inject(ConfigService);
  private readonly groups = inject(GroupsService);
  protected servers = inject(ServersService);

  protected loading$ = new BehaviorSubject<boolean>(false);
  protected saving$ = new BehaviorSubject<boolean>(false);
  protected system: Partial<SystemConfiguration> = {};
  protected isCentral = false;
  protected server: ManagedMasterDto;
  protected serverList: ManagedMasterDto[] = [];
  protected serverNames: string[] = [];

  private subscription: Subscription;

  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;
  @ViewChild('form') public form: NgForm;

  ngOnInit(): void {
    this.subscription = this.areas.registerDirtyable(this, 'panel');
    this.subscription.add(
      this.cfg.isCentral$.subscribe((value) => {
        this.isCentral = value;
      }),
    );

    this.groups.newId().subscribe((r) => {
      this.system.id = r;
      this.loading$.next(false);
    });

    this.subscription.add(
      this.servers.servers$.subscribe((s) => {
        this.serverList = s;
        this.serverNames = this.serverList.map((c) => `${c.hostName} - ${c.description}`);
      }),
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  public isDirty(): boolean {
    return this.form.dirty;
  }

  public canSave(): boolean {
    return this.form.valid;
  }

  protected onSave() {
    this.saving$.next(true);
    this.doSave().subscribe(() => {
      this.reset();
    });
  }

  private reset() {
    this.areas.closePanel();
    this.subscription?.unsubscribe();
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
        }),
      );
  }
}
