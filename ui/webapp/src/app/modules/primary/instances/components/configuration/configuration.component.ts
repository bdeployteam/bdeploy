import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { BehaviorSubject, combineLatest, Observable, Subscription } from 'rxjs';
import { sortNodesMasterFirst } from 'src/app/models/consts';
import { BdDataColumn } from 'src/app/models/data';
import {
  ApplicationValidationDto,
  FlattenedInstanceTemplateConfiguration,
  InstanceConfiguration,
  InstanceNodeConfigurationDto,
  NodeType
} from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { ProductsService } from '../../../products/services/products.service';
import { ServersService } from '../../../servers/services/servers.service';
import { InstanceEditService } from '../../services/instance-edit.service';

import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { InstanceManagedServerComponent } from '../browser/instance-managed-server/instance-managed-server.component';
import { BdButtonComponent } from '../../../../core/components/bd-button/bd-button.component';
import { MatDivider } from '@angular/material/divider';
import {
  BdServerSyncButtonComponent
} from '../../../../core/components/bd-server-sync-button/bd-server-sync-button.component';
import { BdPanelButtonComponent } from '../../../../core/components/bd-panel-button/bd-panel-button.component';
import { MatTooltip } from '@angular/material/tooltip';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import {
  BdNotificationCardComponent
} from '../../../../core/components/bd-notification-card/bd-notification-card.component';
import { BdDataTableComponent } from '../../../../core/components/bd-data-table/bd-data-table.component';
import { BdBannerComponent } from '../../../../core/components/bd-banner/bd-banner.component';
import { ConfigNodeComponent } from './config-node/config-node.component';
import { BdNoDataComponent } from '../../../../core/components/bd-no-data/bd-no-data.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-configuration',
    templateUrl: './configuration.component.html',
    styleUrls: ['./configuration.component.css'],
  imports: [BdDialogComponent, BdDialogToolbarComponent, InstanceManagedServerComponent, BdButtonComponent, MatDivider, BdServerSyncButtonComponent, BdPanelButtonComponent, MatTooltip, BdDialogContentComponent, BdNotificationCardComponent, BdDataTableComponent, BdBannerComponent, ConfigNodeComponent, BdNoDataComponent, RouterLink, AsyncPipe]
})
export class ConfigurationComponent implements OnInit, OnDestroy, DirtyableDialog {
  private readonly media = inject(BreakpointObserver);
  private readonly products = inject(ProductsService);
  private readonly router = inject(Router);
  private readonly groups = inject(GroupsService);
  protected readonly cfg = inject(ConfigService);
  protected readonly areas = inject(NavAreasService);
  protected readonly servers = inject(ServersService);
  protected readonly edit = inject(InstanceEditService);
  protected readonly auth = inject(AuthenticationService);

  private readonly issueColApp: BdDataColumn<ApplicationValidationDto, string> = {
    id: 'app',
    name: 'Application / File',
    data: (r) => this.getApplicationName(r.appId),
    width: '150px',
    classes: (r) => (!r.appId ? ['bd-hint-text'] : []),
  };

  private readonly issueColParam: BdDataColumn<ApplicationValidationDto, string> = {
    id: 'param',
    name: 'Parameter',
    data: (r) => r.paramId,
    width: '200px',
  };

  private readonly issueColMsg: BdDataColumn<ApplicationValidationDto, string> = {
    id: 'msg',
    name: 'Message',
    data: (r) => r.message,
  };

  private readonly issueColDismiss: BdDataColumn<ApplicationValidationDto, string> = {
    id: 'dismiss',
    name: 'Dismiss',
    data: () => 'Dismiss this update message',
    action: (r) => this.edit.dismissUpdateIssue(r),
    icon: () => 'delete',
    width: '40px',
  };

  protected readonly issuesColumns: BdDataColumn<ApplicationValidationDto, unknown>[] = [
    this.issueColApp,
    this.issueColParam,
    this.issueColMsg,
    this.issueColDismiss,
  ];
  protected readonly validationColumns: BdDataColumn<ApplicationValidationDto, unknown>[] = [
    this.issueColApp,
    this.issueColParam,
    this.issueColMsg,
  ];

  protected narrow$ = new BehaviorSubject<boolean>(true);
  protected dirtyMarker$ = new BehaviorSubject<string>('');

  protected config$ = new BehaviorSubject<InstanceConfiguration>(null);
  protected serverNodes$ = new BehaviorSubject<InstanceNodeConfigurationDto[]>([]);
  protected clientNode$ = new BehaviorSubject<InstanceNodeConfigurationDto>(null);

  protected templates$ = new BehaviorSubject<FlattenedInstanceTemplateConfiguration[]>(null);
  protected isEmptyInstance$ = new BehaviorSubject<boolean>(false);

  protected newerProductVerionsAvailable$ = new BehaviorSubject<boolean>(false);

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;

  private subscription: Subscription;
  protected isCentral = false;

  ngOnInit(): void {
    this.subscription = this.media.observe('(max-width:700px)').subscribe((bs) => this.narrow$.next(bs.matches));
    this.subscription.add(this.areas.registerDirtyable(this, 'primary'));
    this.subscription.add(
      this.cfg.isCentral$.subscribe((value) => {
        this.isCentral = value;
      }),
    );

    this.subscription.add(
      combineLatest([this.edit.state$, this.products.products$, this.edit.hasSaveableChanges$]).subscribe(
        ([state, products, saveable]) => {
          if (!state || !products) {
            this.config$.next(null);
            this.serverNodes$.next([]);
            this.clientNode$.next(null);
          } else {
            this.config$.next(state.config.config);
            this.dirtyMarker$.next(this.edit.hasPendingChanges() || saveable ? '*' : '');

            this.serverNodes$.next(
              state.config.nodeDtos
                .filter((p) => !this.isClientNode(p))
                .sort((a, b) => sortNodesMasterFirst(a.nodeName, b.nodeName)),
            );
            this.clientNode$.next(state.config.nodeDtos.find((n) => this.isClientNode(n)));

            const prod = products.find(
              (p) => p.key.name === state.config.config.product.name && p.key.tag === state.config.config.product.tag,
            );
            if (prod) {
              this.templates$.next(prod.instanceTemplates);
            }

            this.edit.requestValidation();
          }
          this.isEmptyInstance$.next(this.isEmptyInstance());
        },
      ),
    );
    this.subscription.add(
      combineLatest([this.edit.current$, this.edit.state$, this.edit.productUpdates$]).subscribe(
        ([current, state, updates]) => {
          if (!current || !state) {
            this.newerProductVerionsAvailable$.next(false);
            return;
          }
          if (current.instanceConfiguration.product.tag !== state.config.config.product.tag) {
            this.newerProductVerionsAvailable$.next(false);
            return;
          }
          const product = current.instanceConfiguration.product;
          const newerVersionOnManaged = current?.managedServer?.productUpdates?.newerVersionAvailable[product.name];
          this.newerProductVerionsAvailable$.next(updates.newerVersionAvailable || newerVersionOnManaged);
        },
      ),
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
    this.edit.reset();
  }

  public isDirty(): boolean {
    // don't check pending changes - those have to be handled in panels, or concealed directly (e.g. process move).
    // if pending unconcealed changes are present we ignore them. otherwise a "unsaved changes" dialog will pop up
    // in the main dialog instead of the panel containing the changes.
    return this.edit.hasSaveableChanges$.value;
  }

  public canSave(): boolean {
    return this.servers.isCurrentInstanceSynchronized$.value &&
      this.edit.hasSaveableChanges$.value &&
      !this.edit.incompatible$.value &&
      !this.edit.validating$.value &&
      !this.edit.issues$.value?.length;
  }

  protected onSave() {
    this.doSave().subscribe(() => {
      // after save navigate back to the dashboard - this will take the user where they will likely want to continue
      // anyway (install, activate, start processes, etc.)
      this.router.navigate([
        'instances',
        'dashboard',
        this.areas.groupContext$.value,
        this.areas.instanceContext$.value,
      ]);
    });
  }

  public doSave(): Observable<unknown> {
    return this.edit.save();
  }

  private isClientNode(node: InstanceNodeConfigurationDto) {
    return node.nodeConfiguration.nodeType === NodeType.CLIENT;
  }

  private isEmptyInstance() {
    if (!this.edit.state$.value?.config?.nodeDtos?.length) {
      return true;
    }

    for (const node of this.edit.state$.value.config.nodeDtos) {
      if (node.nodeConfiguration?.applications?.length) {
        return false;
      }
    }
    return true;
  }

  protected doTrack(index: number, node: InstanceNodeConfigurationDto) {
    return node.nodeName;
  }

  protected getApplicationName(id: string) {
    if (!id) {
      return 'Global';
    }

    const cfg = this.edit.getApplicationConfiguration(id);
    if (!cfg) {
      return id;
    }

    return cfg.name;
  }

  protected goToProductImport(repo: string) {
    const product = this.edit.current$.value?.instanceConfiguration.product.name;
    this.areas.navigateBoth(
      ['products', 'browser', this.groups.current$.value.name],
      ['panels', 'products', 'transfer'],
      {},
      { queryParams: { product, repo } },
    );
  }
}
