import { AfterViewInit, Component, OnDestroy, OnInit, QueryList, ViewChild, ViewChildren, inject } from '@angular/core';
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
import { buildCompletionPrefixes, buildCompletions } from 'src/app/modules/core/utils/completion.utils';
import { getPreRenderable, getRenderPreview } from 'src/app/modules/core/utils/linked-values.utils';
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
export class ConfigureEndpointsComponent implements DirtyableDialog, OnInit, OnDestroy, AfterViewInit {
  private readonly systems = inject(SystemsService);
  private readonly areas = inject(NavAreasService);
  protected readonly edit = inject(ProcessEditService);
  protected readonly instanceEdit = inject(InstanceEditService);

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild(BdDialogToolbarComponent) private readonly tb: BdDialogToolbarComponent;
  @ViewChildren('epForm') private readonly forms: QueryList<NgForm>;

  protected hasPendingChanges: boolean;
  protected isFromInvalid: boolean;

  protected process: ApplicationConfiguration;
  protected instance: InstanceConfigurationDto;
  protected system: SystemConfiguration;

  protected readonly TYPE_STRING = ParameterType.STRING;
  protected readonly TYPE_PASSWORD = ParameterType.PASSWORD;
  protected readonly TYPE_PORT = ParameterType.SERVER_PORT;
  protected readonly TYPE_BOOLEAN = ParameterType.BOOLEAN;

  protected completionPrefixes = buildCompletionPrefixes();
  protected completions: ContentCompletion[];

  protected authTypeValues = Object.keys(HttpAuthenticationType);
  protected authTypes: HttpAuthenticationType[] = [];
  protected endpointDisabledStatus = new Map<HttpEndpoint, HttpEndpointDisabledStatus>();

  private subscription: Subscription;

  ngOnInit() {
    this.subscription = combineLatest([this.edit.process$, this.instanceEdit.state$, this.systems.systems$]).subscribe(
      ([appConfig, editState, systemConfigDto]) => {
        this.process = appConfig;
        this.instance = editState?.config;

        if (!editState?.config?.config?.system || !systemConfigDto?.length) {
          this.system = null;
        } else {
          this.system = systemConfigDto.find(
            (x) =>
              x.key.name === editState.config.config.system.name && x.key.tag === editState.config.config.system.tag,
          )?.config;
        }

        this.completions = buildCompletions(
          this.completionPrefixes,
          this.instance,
          this.system,
          this.process,
          this.instanceEdit.stateApplications$.value,
        );

        if (appConfig?.endpoints?.http?.length) {
          for (let i = 0; i < appConfig.endpoints.http.length; ++i) {
            this.onChangeAuthType(appConfig.endpoints.http[i].authType, i);
            this.calculateDisabledStatus(appConfig.endpoints.http[i]);
          }
        }
      },
    );

    this.subscription.add(this.areas.registerDirtyable(this, 'panel'));
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
        }),
      );
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  public isDirty(): boolean {
    return this.instanceEdit.hasPendingChanges();
  }

  public isInvalid(): boolean {
    return this.forms.filter((f) => f.invalid).length !== 0;
  }

  protected onSave() {
    this.doSave().subscribe(() => this.tb.closePanel());
  }

  public canSave(): boolean {
    return this.isDirty() && !this.isFromInvalid;
  }

  public doSave(): Observable<unknown> {
    return of(true).pipe(
      tap(() => {
        this.instanceEdit.conceal('Change endpoint configuration');
      }),
    );
  }

  protected isSecure(ep: HttpEndpoint) {
    const strVal = getRenderPreview(ep.secure, this.process, this.instance, this.system);
    return strVal === 'true';
  }

  protected onChangeAuthType(type: LinkedValueConfiguration, index: number) {
    const exp = getRenderPreview(type, this.process, this.instance, this.system);
    for (const x of Object.values(HttpAuthenticationType)) {
      if (x === exp) {
        this.authTypes[index] = x;
        return;
      }
    }
    this.authTypes[index] = HttpAuthenticationType.NONE;
  }

  private calculateDisabledStatus(e: HttpEndpoint) {
    const enabledPreview = getRenderPreview(e.enabled, this.process, this.instance, this.system);
    const disabled = !enabledPreview || enabledPreview === 'false' || !!enabledPreview.match(/{{([^}]+)}}/g);
    const reason = disabled
      ? `This endpoint is disabled due to a missing prerequisite (${getPreRenderable(e.enabled)}).`
      : undefined;
    this.endpointDisabledStatus.set(e, { disabled, reason });
  }
}
