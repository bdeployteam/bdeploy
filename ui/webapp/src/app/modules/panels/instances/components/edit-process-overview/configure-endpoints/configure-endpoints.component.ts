import {
  AfterViewInit,
  Component,
  OnDestroy,
  QueryList,
  ViewChild,
  ViewChildren,
} from '@angular/core';
import { NgForm } from '@angular/forms';
import { Observable, Subscription, combineLatest, of } from 'rxjs';
import { debounceTime, tap } from 'rxjs/operators';
import {
  ApplicationConfiguration,
  HttpAuthenticationType,
  HttpEndpoint,
  InstanceConfigurationDto,
  LinkedValueConfiguration,
  ParameterType,
  SystemConfiguration,
} from 'src/app/models/gen.dtos';
import { ContentCompletion } from 'src/app/modules/core/components/bd-content-assist-menu/bd-content-assist-menu.component';
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import {
  buildCompletionPrefixes,
  buildCompletions,
} from 'src/app/modules/core/utils/completion.utils';
import { getRenderPreview } from 'src/app/modules/core/utils/linked-values.utils';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { SystemsService } from 'src/app/modules/primary/systems/services/systems.service';
import { ProcessEditService } from '../../../services/process-edit.service';

interface HttpEndpointDisabledStatus {
  disabled: boolean;
  reason: string;
}

@Component({
  selector: 'app-configure-endpoints',
  templateUrl: './configure-endpoints.component.html',
})
export class ConfigureEndpointsComponent
  implements DirtyableDialog, OnDestroy, AfterViewInit
{
  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private tb: BdDialogToolbarComponent;
  @ViewChildren('epForm') private forms: QueryList<NgForm>;

  /* template */ hasPendingChanges: boolean;
  /* template */ isFromInvalid: boolean;

  /* template */ process: ApplicationConfiguration;
  /* template */ instance: InstanceConfigurationDto;
  /* template */ system: SystemConfiguration;

  /* template */ readonly TYPE_STRING = ParameterType.STRING;
  /* template */ readonly TYPE_PASSWORD = ParameterType.PASSWORD;
  /* template */ readonly TYPE_PORT = ParameterType.SERVER_PORT;
  /* template */ readonly TYPE_BOOLEAN = ParameterType.BOOLEAN;

  /* template */ completionPrefixes = buildCompletionPrefixes();
  /* template */ completions: ContentCompletion[];

  /* template */ authTypeValues = Object.keys(HttpAuthenticationType);
  /* template */ authTypes: HttpAuthenticationType[] = [];
  /* template */ endpointDisabledStatus = new Map<
    HttpEndpoint,
    HttpEndpointDisabledStatus
  >();

  private subscription: Subscription;

  constructor(
    public edit: ProcessEditService,
    public instanceEdit: InstanceEditService,
    systems: SystemsService,
    areas: NavAreasService
  ) {
    this.subscription = combineLatest([
      this.edit.process$,
      this.instanceEdit.state$,
      systems.systems$,
    ]).subscribe(([p, i, s]) => {
      this.process = p;
      this.instance = i?.config;

      if (!i?.config?.config?.system || !s?.length) {
        this.system = null;
      } else {
        this.system = s.find(
          (x) =>
            x.key.name === i.config.config.system.name &&
            x.key.tag === i.config.config.system.tag
        )?.config;
      }

      this.completions = buildCompletions(
        this.completionPrefixes,
        this.instance,
        this.system,
        this.process,
        this.instanceEdit.stateApplications$.value
      );

      if (p?.endpoints?.http?.length) {
        for (let i = 0; i < p.endpoints.http.length; ++i) {
          this.onChangeAuthType(p.endpoints.http[i].authType, i);
          this.calculateDisabledStatus(p.endpoints.http[i]);
        }
      }
    });

    this.subscription.add(areas.registerDirtyable(this, 'panel'));
  }

  ngAfterViewInit(): void {
    if (!this.forms) {
      return;
    }
    this.forms.forEach((form) => {
      this.subscription.add(
        form.statusChanges.pipe(debounceTime(100)).subscribe((status) => {
          this.isFromInvalid = status === 'INVALID';
          this.hasPendingChanges = this.isDirty();
        })
      );
    });
  }

  public isDirty(): boolean {
    return this.instanceEdit.hasPendingChanges();
  }

  public isInvalid(): boolean {
    return this.forms.filter((f) => f.invalid).length !== 0;
  }

  /* template */ onSave() {
    this.doSave().subscribe(() => this.tb.closePanel());
  }

  public canSave(): boolean {
    return this.isDirty() && !this.isFromInvalid;
  }

  public doSave(): Observable<any> {
    return of(true).pipe(
      tap(() => {
        this.instanceEdit.conceal('Change endpoint configuration');
      })
    );
  }

  ngOnDestroy(): void {
    if (this.subscription) {
      this.subscription.unsubscribe();
    }
  }

  /* template */ isSecure(ep: HttpEndpoint) {
    const strVal = getRenderPreview(
      ep.secure,
      this.process,
      this.instance,
      this.system
    );
    return strVal === 'true';
  }

  /* template */ onChangeAuthType(
    type: LinkedValueConfiguration,
    index: number
  ) {
    const exp = getRenderPreview(
      type,
      this.process,
      this.instance,
      this.system
    );
    for (const x of Object.values(HttpAuthenticationType)) {
      if (x === exp) {
        this.authTypes[index] = x;
        return;
      }
    }
    this.authTypes[index] = HttpAuthenticationType.NONE;
  }

  private calculateDisabledStatus(e: HttpEndpoint) {
    const enabledPreview = getRenderPreview(
      e.enabled,
      this.process,
      this.instance,
      this.system
    );
    const disabled = !enabledPreview || enabledPreview === 'false';
    const reason = disabled
      ? `This endpoint is disabled. Enabled flag value: ${e.enabled.value}, expression: ${e.enabled.linkExpression}, preview ${enabledPreview}.`
      : undefined;
    this.endpointDisabledStatus.set(e, { disabled, reason });
  }
}
