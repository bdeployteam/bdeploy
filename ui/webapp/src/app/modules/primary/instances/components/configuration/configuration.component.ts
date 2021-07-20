import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject, combineLatest, Observable, Subscription } from 'rxjs';
import { CLIENT_NODE_NAME, sortNodesMasterFirst } from 'src/app/models/consts';
import { BdDataColumn } from 'src/app/models/data';
import { ApplicationValidationDto, InstanceConfiguration, InstanceNodeConfigurationDto, InstanceTemplateDescriptor } from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { ProductsService } from '../../../products/services/products.service';
import { ServersService } from '../../../servers/services/servers.service';
import { InstanceEditService } from '../../services/instance-edit.service';

@Component({
  selector: 'app-configuration',
  templateUrl: './configuration.component.html',
  styleUrls: ['./configuration.component.css'],
})
export class ConfigurationComponent implements OnInit, OnDestroy, DirtyableDialog {
  private readonly issueColApp: BdDataColumn<ApplicationValidationDto> = {
    id: 'app',
    name: 'Application',
    data: (r) => this.getApplicationName(r.appUid),
    width: '150px',
    classes: (r) => (!r.appUid ? ['bd-hint-text'] : []),
  };

  private readonly issueColParam: BdDataColumn<ApplicationValidationDto> = {
    id: 'param',
    name: 'Parameter',
    data: (r) => r.paramUid,
    width: '200px',
  };

  private readonly issueColMsg: BdDataColumn<ApplicationValidationDto> = {
    id: 'msg',
    name: 'Message',
    data: (r) => r.message,
  };

  private readonly issueColDismiss: BdDataColumn<ApplicationValidationDto> = {
    id: 'dismiss',
    name: 'Dismiss',
    data: (r) => 'Dismiss this update message',
    action: (r) => this.edit.dismissUpdateIssue(r),
    icon: (r) => 'delete',
    width: '40px',
  };

  /* template */ issuesColumns: BdDataColumn<ApplicationValidationDto>[] = [this.issueColApp, this.issueColParam, this.issueColMsg, this.issueColDismiss];
  /* template */ validationColumns: BdDataColumn<ApplicationValidationDto>[] = [this.issueColApp, this.issueColParam, this.issueColMsg];

  /* template */ narrow$ = new BehaviorSubject<boolean>(true);
  /* template */ headerName$ = new BehaviorSubject<string>('Loading');

  /* template */ config$ = new BehaviorSubject<InstanceConfiguration>(null);
  /* template */ serverNodes$ = new BehaviorSubject<InstanceNodeConfigurationDto[]>([]);
  /* template */ clientNode$ = new BehaviorSubject<InstanceNodeConfigurationDto>(null);

  /* template */ templates$ = new BehaviorSubject<InstanceTemplateDescriptor[]>(null);

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;

  private subscription: Subscription;

  constructor(
    public cfg: ConfigService,
    public areas: NavAreasService,
    public servers: ServersService,
    public edit: InstanceEditService,
    private media: BreakpointObserver,
    private products: ProductsService
  ) {
    this.subscription = this.media.observe('(max-width:700px)').subscribe((bs) => this.narrow$.next(bs.matches));
    this.subscription.add(this.areas.registerDirtyable(this, 'primary'));
  }

  ngOnInit(): void {
    this.subscription.add(
      combineLatest([this.edit.state$, this.products.products$]).subscribe(([state, products]) => {
        if (!state || !products) {
          this.config$.next(null);
          this.serverNodes$.next([]);
          this.clientNode$.next(null);
        } else {
          this.config$.next(state.config.config);
          this.headerName$.next(this.edit.hasPendingChanges() || this.edit.hasSaveableChanges() ? `${state.config.config.name}*` : state.config.config.name);

          this.serverNodes$.next(state.config.nodeDtos.filter((p) => !this.isClientNode(p)).sort((a, b) => sortNodesMasterFirst(a.nodeName, b.nodeName)));
          this.clientNode$.next(state.config.nodeDtos.find((n) => this.isClientNode(n)));

          const prod = products.find((p) => p.key.name === state.config.config.product.name && p.key.tag === state.config.config.product.tag);
          if (!!prod) {
            this.templates$.next(prod.instanceTemplates);
          }

          this.edit.requestValidation();
        }
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    this.edit.reset();
  }

  public isDirty(): boolean {
    // don't check pending changes - those have to be handled in panels, or concealed directly (e.g. process move).
    // if pending unconcealed changes are present we ignore them. otherwise a "unsaved changes" dialog will pop up
    // in the main dialog instead of the panel containing the changes.
    return this.edit.hasSaveableChanges();
  }

  /* template */ onSave() {
    this.doSave().subscribe();
  }

  public doSave(): Observable<any> {
    return this.edit.save();
  }

  private isClientNode(node: InstanceNodeConfigurationDto) {
    return node.nodeName === CLIENT_NODE_NAME;
  }

  /* template */ isEmptyInstance() {
    if (!this.edit.state$.value?.config?.nodeDtos?.length) {
      return true;
    }

    for (const node of this.edit.state$.value?.config.nodeDtos) {
      if (!!node.nodeConfiguration?.applications?.length) {
        return false;
      }
    }
    return true;
  }

  /* template */ doTrack(index: number, node: InstanceNodeConfigurationDto) {
    return node.nodeName;
  }

  /* template */ getApplicationName(uid: string) {
    if (!uid) {
      return 'Global';
    }

    const cfg = this.edit.getApplicationConfiguration(uid);
    if (!cfg) {
      return uid;
    }

    return cfg.name;
  }
}
