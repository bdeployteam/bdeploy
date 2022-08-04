import {
  AfterViewInit,
  Component,
  OnDestroy,
  QueryList,
  ViewChild,
  ViewChildren,
} from '@angular/core';
import { NgForm } from '@angular/forms';
import {
  BehaviorSubject,
  combineLatest,
  Observable,
  of,
  Subscription,
} from 'rxjs';
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
import { BdDialogToolbarComponent } from 'src/app/modules/core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { BdPopupDirective } from 'src/app/modules/core/components/bd-popup/bd-popup.directive';
import { DirtyableDialog } from 'src/app/modules/core/guards/dirty-dialog.guard';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { getRenderPreview } from 'src/app/modules/core/utils/linked-values.utils';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { SystemsService } from 'src/app/modules/primary/systems/services/systems.service';
import { ProcessEditService } from '../../../services/process-edit.service';

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

  /* template */ authTypeValues = Object.keys(HttpAuthenticationType);
  /* template */ authTypeLabels = Object.keys(HttpAuthenticationType).map(
    (t) => t.substring(0, 1) + t.substring(1).toLowerCase()
  );
  /* template */ hasPendingChanges: boolean;
  /* template */ isFromInvalid: boolean;

  /* template */ process: ApplicationConfiguration;
  /* template */ instance: InstanceConfigurationDto;
  /* template */ system: SystemConfiguration;
  /* template */ linkEditorPopup$ = new BehaviorSubject<BdPopupDirective>(null);
  /* template */ currentInput: LinkedValueConfiguration;

  /* template */ readonly TYPE_STRING = ParameterType.STRING;
  /* template */ readonly TYPE_PORT = ParameterType.SERVER_PORT;
  /* template */ readonly TYPE_BOOLEAN = ParameterType.BOOLEAN;

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
        return;
      }

      this.system = s.find(
        (x) =>
          x.key.name === i.config.config.system.name &&
          x.key.tag === i.config.config.system.tag
      )?.config;
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

  private makeValueLink(p: LinkedValueConfiguration, isPass: boolean) {
    p.linkExpression = isPass ? '' : p.value || '';
    p.value = null;
  }

  private makeValuePlain(p: LinkedValueConfiguration, isPass: boolean) {
    if (p.linkExpression?.indexOf('{{') >= 0) {
      p.value = isPass
        ? ''
        : getRenderPreview(p, this.process, this.instance, this.system);
      p.linkExpression = null;
    } else {
      p.value = isPass ? '' : p.linkExpression || '';
      p.linkExpression = null;
    }
  }

  /* template */ isLink(p: LinkedValueConfiguration) {
    return p?.linkExpression !== null;
  }

  /* template */ toggleLink(
    p: LinkedValueConfiguration,
    link: boolean,
    isPass: boolean
  ) {
    if (link) {
      this.makeValuePlain(p, isPass);
    } else {
      this.makeValueLink(p, isPass);
    }
  }

  /* template */ appendLink(val: string) {
    if (!this.currentInput) {
      return;
    }
    this.currentInput.linkExpression += val;
  }
}
