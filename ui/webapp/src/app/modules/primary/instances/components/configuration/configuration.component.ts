import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { CLIENT_NODE_NAME, sortNodesMasterFirst } from 'src/app/models/consts';
import { InstanceConfiguration, InstanceNodeConfigurationDto, InstanceStateRecord } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { ServersService } from '../../../servers/services/servers.service';
import { InstanceEditService } from '../../services/instance-edit.service';
import { InstanceStateService } from '../../services/instance-state.service';

@Component({
  selector: 'app-configuration',
  templateUrl: './configuration.component.html',
  styleUrls: ['./configuration.component.css'],
})
export class ConfigurationComponent implements OnInit, OnDestroy, DirtyableDialog {
  /* template */ narrow$ = new BehaviorSubject<boolean>(true);
  /* template */ headerName$ = new BehaviorSubject<string>('Loading');

  /* template */ config$ = new BehaviorSubject<InstanceConfiguration>(null);
  /* template */ serverNodes$ = new BehaviorSubject<InstanceNodeConfigurationDto[]>([]);
  /* template */ clientNode$ = new BehaviorSubject<InstanceNodeConfigurationDto>(null);

  /* template */ states$ = new BehaviorSubject<InstanceStateRecord>(null);
  /* template */ installing$ = new BehaviorSubject<boolean>(false);
  /* template */ activating$ = new BehaviorSubject<boolean>(false);

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;

  private subscription: Subscription;

  constructor(
    public cfg: ConfigService,
    public areas: NavAreasService,
    public servers: ServersService,
    public edit: InstanceEditService,
    private media: BreakpointObserver,
    private states: InstanceStateService
  ) {
    this.subscription = this.media.observe('(max-width:700px)').subscribe((bs) => this.narrow$.next(bs.matches));
  }

  ngOnInit(): void {
    this.subscription.add(
      this.edit.state$.subscribe((state) => {
        if (!state) {
          this.config$.next(null);
          this.serverNodes$.next([]);
          this.clientNode$.next(null);
        } else {
          this.config$.next(state.config);
          this.headerName$.next(this.edit.hasPendingChanges() || this.edit.hasSaveableChanges() ? `${state.config.name}*` : state.config.name);

          this.serverNodes$.next(state.nodeDtos.filter((p) => !this.isClientNode(p)).sort((a, b) => sortNodesMasterFirst(a.nodeName, b.nodeName)));
          this.clientNode$.next(state.nodeDtos.find((n) => this.isClientNode(n)));
        }
      })
    );

    this.subscription.add(this.states.state$.subscribe((s) => this.states$.next(s)));
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    this.edit.reset();
  }

  public isDirty(): boolean {
    return this.edit.hasSaveableChanges() || this.edit.hasPendingChanges();
  }

  private isClientNode(node: InstanceNodeConfigurationDto) {
    return node.nodeName === CLIENT_NODE_NAME;
  }

  /* template */ isInstalled(version: string) {
    return !!this.states$.value?.installedTags?.find((s) => s === version);
  }

  /* template */ doInstall(version: string) {
    this.installing$.next(true);
    this.states
      .install(version)
      .pipe(finalize(() => this.installing$.next(false)))
      .subscribe();
  }

  /* template */ doActivate(version: string) {
    this.activating$.next(true);
    this.states
      .activate(version)
      .pipe(finalize(() => this.activating$.next(false)))
      .subscribe();
  }

  /* template */ doTrack(index: number, node: InstanceNodeConfigurationDto) {
    return node.nodeName;
  }
}
