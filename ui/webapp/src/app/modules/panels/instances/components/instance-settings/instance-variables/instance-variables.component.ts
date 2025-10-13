import { ChangeDetectionStrategy, Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { BehaviorSubject, combineLatest, Observable, of, Subscription, tap } from 'rxjs';
import {
  ApplicationDto,
  InstanceConfigurationDto,
  InstanceDto,
  SystemConfiguration,
  VariableConfiguration,
  Version
} from 'src/app/models/gen.dtos';
import {
  ContentCompletion
} from 'src/app/modules/core/components/bd-content-assist-menu/bd-content-assist-menu.component';
import {
  BdDialogToolbarComponent
} from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { PluginService } from 'src/app/modules/core/services/plugin.service';
import { buildCompletionPrefixes, buildCompletions } from 'src/app/modules/core/utils/completion.utils';
import { groupVariables, VariableGroup } from 'src/app/modules/core/utils/variable-utils';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { SystemsService } from 'src/app/modules/primary/systems/services/systems.service';


import { BdButtonComponent } from '../../../../../core/components/bd-button/bd-button.component';
import { MatDivider } from '@angular/material/divider';
import { BdDialogContentComponent } from '../../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import {
  BdVariableGroupsComponent
} from '../../../../../core/components/bd-variable-groups/bd-variable-groups.component';
import { AsyncPipe } from '@angular/common';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { compareVersions } from 'src/app/modules/core/utils/version.utils';

const SYSTEM_VARIABLE_OVERWRITE_VERSION: Version = {
  major: 7,
  minor: 6,
  micro: 0,
  qualifier: null
}

@Component({
    selector: 'app-instance-variables',
    templateUrl: './instance-variables.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BdDialogComponent, BdDialogToolbarComponent, BdButtonComponent, MatDivider, BdDialogContentComponent, BdVariableGroupsComponent, AsyncPipe]
})
export class InstanceVariablesComponent implements DirtyableDialog, OnInit, OnDestroy {
  private readonly cfg = inject(ConfigService);
  private readonly systems = inject(SystemsService);
  private readonly areas = inject(NavAreasService);
  private readonly plugins = inject(PluginService);
  private readonly products = inject(ProductsService);
  private readonly instances = inject(InstancesService);
  protected readonly edit = inject(InstanceEditService);
  protected readonly groups = inject(GroupsService);

  protected search: string;

  protected instance: InstanceConfigurationDto;
  protected system$ = new BehaviorSubject<SystemConfiguration>(null);
  protected apps: ApplicationDto[];
  protected editorValues: string[];

  protected completions: ContentCompletion[];
  protected completionPrefixes = buildCompletionPrefixes();

  protected groups$ = new BehaviorSubject<VariableGroup[]>([]);

  protected overwriteIds: string[];

  private subscription: Subscription;

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) tb: BdDialogToolbarComponent;

  ngOnInit() {
    this.subscription = combineLatest([
      this.edit.state$,
      this.edit.stateApplications$,
      this.systems.systems$,
      this.products.products$,
    ]).subscribe(([instance, apps, systems, products]) => {
      if (instance?.config) {
        this.instance = instance.config;
        this.apps = apps;

        if (instance?.config?.config?.system && systems?.length) {
          this.system$.next(systems.find((s) => s.key.name === instance.config.config.system.name)?.config);
        }

        this.plugins
          .getAvailableEditorTypes(this.groups.current$?.value?.name, instance.config.config.product)
          .subscribe((editors) => {
            this.editorValues = editors;
          });

        const product = instance.config.config.product;
        const descriptors = products.find(
          (p) => p.key.name === product.name && p.key.tag === product.tag,
        ).instanceVariables;
        this.groups$.next(groupVariables(descriptors, instance.config.config.instanceVariables));
        this.buildCompletions();
      }
    });

    this.subscription.add(combineLatest([this.system$, this.cfg.isCentral$, this.instances.current$]).subscribe(([system, isCentral, current]) => {
      const systemVariableIds = system?.systemVariables?.map((sv) => sv.id);
      this.overwriteIds = this.canSystemOverwrite(isCentral, current) ? systemVariableIds : null;
    }));

    this.subscription.add(this.areas.registerDirtyable(this, 'panel'));
  }

  // system variable overwrite is not supported if managed server is older than 7.6.0
  private canSystemOverwrite(isCentral: boolean, current: InstanceDto): boolean {
    if (!isCentral || !current) {
      return true;
    }
    const minions = current.managedServer?.nodes?.nodes;
    if (!minions) {
      return true;
    }
    const hasOldMinion = Object.keys(minions).map((key) => minions[key]?.config).some((minion) => {
      try {
        return compareVersions(minion.version, SYSTEM_VARIABLE_OVERWRITE_VERSION) < 0;
      } catch (e) {
        console.error('Failed to parse minion version', e);
        return false;
      }
    });
    return !hasOldMinion;
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  private buildCompletions() {
    this.completions = buildCompletions(this.completionPrefixes, this.instance, this.system$.value, null, this.apps);
  }

  public isDirty(): boolean {
    return this.edit.hasPendingChanges();
  }

  protected onSave() {
    this.doSave().subscribe(() => this.tb.closePanel());
  }

  public doSave(): Observable<unknown> {
    return of(true).pipe(
      tap(() => {
        this.edit.conceal('Change Instance Variables');
      }),
    );
  }

  protected onVariableListChange(varList: VariableConfiguration[]) {
    const state = this.edit.state$.value;
    state.config.config.instanceVariables = varList;
    this.edit.state$.next(state);
  }
}
