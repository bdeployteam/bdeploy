import { ChangeDetectionStrategy, Component, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, combineLatest, finalize, Observable, of, Subscription, switchMap } from 'rxjs';
import { InstanceDto, SystemConfigurationDto } from 'src/app/models/gen.dtos';
import {
  ContentCompletion
} from 'src/app/modules/core/components/bd-content-assist-menu/bd-content-assist-menu.component';
import {
  BdDialogToolbarComponent
} from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { buildCompletionPrefixes, buildCompletions } from 'src/app/modules/core/utils/completion.utils';
import { isDirty } from 'src/app/modules/core/utils/dirty.utils';
import { groupVariables, VariableGroup } from 'src/app/modules/core/utils/variable-utils';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { SystemsEditService } from '../../../services/systems-edit.service';
import { VariableConfiguration } from './../../../../../../models/gen.dtos';


import { BdButtonComponent } from '../../../../../core/components/bd-button/bd-button.component';
import { MatDivider } from '@angular/material/divider';
import { BdDialogContentComponent } from '../../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import {
  BdVariableGroupsComponent
} from '../../../../../core/components/bd-variable-groups/bd-variable-groups.component';
import { AsyncPipe } from '@angular/common';

const MAGIC_ABORT = 'abort_save';

@Component({
    selector: 'app-system-variables',
    templateUrl: './system-variables.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
      BdDialogComponent,
      BdDialogToolbarComponent,
        BdButtonComponent,
        MatDivider,
        BdDialogContentComponent,
        BdVariableGroupsComponent,
        AsyncPipe,
    ],
})
export class SystemVariablesComponent implements DirtyableDialog, OnInit, OnDestroy {
  private readonly instances = inject(InstancesService);
  private readonly areas = inject(NavAreasService);
  protected readonly auth = inject(AuthenticationService);
  protected readonly edit = inject(SystemsEditService);

  private orig: SystemConfigurationDto;
  protected system: SystemConfigurationDto;
  protected saving$ = new BehaviorSubject<boolean>(false);

  protected groups$ = new BehaviorSubject<VariableGroup[]>([]);
  protected completionPrefixes = buildCompletionPrefixes();
  protected completions: ContentCompletion[];
  protected suggestedIds: string[];

  private subscription: Subscription;
  private readonly instancesUsing$ = new BehaviorSubject<InstanceDto[]>([]);

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) tb: BdDialogToolbarComponent;

  ngOnInit() {
    this.subscription = this.edit.current$.subscribe((c) => {
      if (!c) {
        return;
      }

      this.system = cloneDeep(c);
      this.orig = cloneDeep(c);

      this.groups$.next(
        groupVariables(this.system.config.systemVariableDefinitions, this.system.config.systemVariables),
      );
      this.buildCompletions();
    });

    this.subscription.add(
      combineLatest([this.edit.current$, this.instances.instances$]).subscribe(([systemConfigDto, instanceDto]) => {
        if (!systemConfigDto || !instanceDto) {
          this.instancesUsing$.next([]);
          return;
        }

        this.instancesUsing$.next(instanceDto.filter(
          (i) => i.instanceConfiguration?.system?.name === systemConfigDto.key?.name,
        ));
      }),
    );

    this.subscription.add(combineLatest([this.groups$, this.instancesUsing$]).subscribe(([groups, instances]) => {
      const instanceVariableIds = [...new Set(instances.flatMap((i) => i.instanceConfiguration.instanceVariables).map((iv) => iv.id))];
      const systemVariableIds = groups.flatMap((g) => g.pairs).map((p) => p.descriptor?.id || p.value.id);
      this.suggestedIds = instanceVariableIds.filter((id) => !systemVariableIds.includes(id));
    }));

    this.subscription.add(this.areas.registerDirtyable(this, 'panel'));
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  private buildCompletions() {
    this.completions = buildCompletions(this.completionPrefixes, null, this.system.config, null, null);
  }

  protected onVariableListChange(varList: VariableConfiguration[]) {
    this.system.config.systemVariables = varList;
    this.groups$.next(groupVariables(this.system.config.systemVariableDefinitions, this.system.config.systemVariables));
    this.buildCompletions();
  }

  public isDirty(): boolean {
    return isDirty(this.system, this.orig);
  }

  public doSave(): Observable<unknown> {
    this.saving$.next(true);

    const save = this.edit.update(this.system).pipe(finalize(() => this.saving$.next(false)));
    const instancesUsingCount = this.instancesUsing$.value.length;

    if (instancesUsingCount > 0) {
      return this.dialog
        .confirm(
          `Saving ${instancesUsingCount} instances`,
          `Affected <strong>${instancesUsingCount}</strong> will be updated with the new system version. This needs to be installed and activated on all affected instances.`,
          'warning',
        )
        .pipe(
          switchMap((b) => {
            if (b) {
              return save;
            } else {
              return of(MAGIC_ABORT).pipe(finalize(() => this.saving$.next(false)));
            }
          }),
        );
    } else {
      // no confirmation required
      return save;
    }
  }

  protected onSave(): void {
    this.doSave().subscribe((x) => {
      if (x !== MAGIC_ABORT) {
        this.system = this.orig = null;
        this.tb.closePanel();
      }
    });
  }
}
