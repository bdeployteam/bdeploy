import { AfterViewInit, Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { NgForm } from '@angular/forms';
import { Observable, Subscription, combineLatest, of } from 'rxjs';
import { debounceTime, tap } from 'rxjs/operators';
import { InstancePurpose, ManifestKey } from 'src/app/models/gen.dtos';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { GlobalEditState, InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { SystemsService } from 'src/app/modules/primary/systems/services/systems.service';

@Component({
  selector: 'app-edit-config',
  templateUrl: './edit-config.component.html',
})
export class EditConfigComponent implements OnInit, OnDestroy, DirtyableDialog, AfterViewInit {
  private readonly areas = inject(NavAreasService);
  protected readonly cfg = inject(ConfigService);
  protected readonly edit = inject(InstanceEditService);
  protected readonly servers = inject(ServersService);
  protected readonly systems = inject(SystemsService);

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private readonly tb: BdDialogToolbarComponent;
  @ViewChild('form') public form: NgForm;

  private subscription: Subscription;

  protected purposes = [InstancePurpose.PRODUCTIVE, InstancePurpose.DEVELOPMENT, InstancePurpose.TEST];
  protected hasPendingChanges: boolean;

  protected systemKeys: ManifestKey[];
  protected systemLabels: string[];
  protected systemSel: ManifestKey;

  ngOnInit() {
    this.subscription = this.areas.registerDirtyable(this, 'panel');
    this.subscription.add(
      combineLatest([this.systems.systems$, this.edit.state$, this.edit.current$]).subscribe(([s, i, dto]) => {
        if (!s?.length || !i) {
          return;
        }

        const serverName = dto?.managedServer?.hostName;
        const filtered = s.filter((x) => !x.minion || x.minion === serverName);

        this.systemKeys = filtered.map((configDto) => configDto.key);
        this.systemLabels = filtered.map((configDto) => `${configDto.config.name} (${configDto.config.description})`);

        if (i.config.config.system) {
          this.systemSel = this.systemKeys.find((k) => k.name === i.config.config.system.name);
        }
      }),
    );
  }

  ngAfterViewInit(): void {
    if (!this.form) {
      return;
    }
    this.subscription.add(
      this.form.valueChanges.pipe(debounceTime(100)).subscribe(() => {
        this.hasPendingChanges = this.isDirty();
      }),
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  public isDirty(): boolean {
    return this.edit.hasPendingChanges();
  }

  public canSave(): boolean {
    return this.form.valid;
  }

  protected onSave() {
    this.doSave().subscribe(() => this.tb.closePanel());
  }

  protected onSystemChange(state: GlobalEditState, value: ManifestKey) {
    state.config.config.system = value;
  }

  public doSave(): Observable<unknown> {
    return of(true).pipe(
      tap(() => {
        this.edit.conceal('Change Instance Configuration');
      }),
    );
  }
}
