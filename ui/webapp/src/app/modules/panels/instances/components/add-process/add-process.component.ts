import { Component, OnDestroy, OnInit, TemplateRef, ViewChild, ViewEncapsulation, inject } from '@angular/core';
import { BehaviorSubject, Observable, Subscription, combineLatest, interval, of } from 'rxjs';
import { distinctUntilChanged, finalize } from 'rxjs/operators';
import { CLIENT_NODE_NAME } from 'src/app/models/consts';
import { BdDataColumn } from 'src/app/models/data';
import {
  ApplicationConfiguration,
  ApplicationDto,
  ApplicationType,
  FlattenedApplicationTemplateConfiguration,
  InstanceNodeConfigurationDto,
  MinionDto,
  OperatingSystem,
} from 'src/app/models/gen.dtos';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { getAppKeyName, getAppOs, updateAppOs } from 'src/app/modules/core/utils/manifest.utils';
import { InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';
import { ServersService } from 'src/app/modules/primary/servers/services/servers.service';
import { ProcessEditService } from '../../services/process-edit.service';
import { AppTemplateNameComponent } from './app-template-name/app-template-name.component';

export interface AppRow {
  app: AppGroup;
  node: InstanceNodeConfigurationDto;
  template: FlattenedApplicationTemplateConfiguration;
}

interface AppGroup {
  appKeyName: string;
  appDisplayName: string;
  availableOs: OperatingSystem[];
  applications: ApplicationDto[];
  templates: FlattenedApplicationTemplateConfiguration[];
}

const colAppName: BdDataColumn<AppRow> = {
  id: 'name',
  name: 'Application',
  data: (r) => (!r.template ? r.app.appDisplayName : r.template.name),
  component: AppTemplateNameComponent,
  tooltipDelay: 120000, // effectively disable: tooltip after two minutes.
};

@Component({
  selector: 'app-add-process',
  templateUrl: './add-process.component.html',
  styleUrls: ['./add-process.component.css'],
  encapsulation: ViewEncapsulation.None,
})
export class AddProcessComponent implements OnInit, OnDestroy {
  private readonly edit = inject(ProcessEditService);
  protected readonly instanceEdit = inject(InstanceEditService);
  protected readonly servers = inject(ServersService);

  private readonly colAdd: BdDataColumn<AppRow> = {
    id: 'add',
    name: 'Add',
    data: (r) => `Add ${r.template ? 'template ' + r.template.name : r.app.appDisplayName} to selected node.`,
    icon: (r) => (r.template ? 'auto_fix_normal' : 'add'),
    action: (r) => this.addProcess(r),
    width: '42px',
  };

  protected loading$ = new BehaviorSubject<boolean>(true);
  protected records$ = new BehaviorSubject<AppRow[]>([]);
  protected readonly columns: BdDataColumn<AppRow>[] = [colAppName, this.colAdd];

  protected selectedTemplate: FlattenedApplicationTemplateConfiguration;
  protected response: { [key: string]: string };

  protected clipBoardCfg$ = new BehaviorSubject<ApplicationConfiguration>(null);
  protected clipBoardError$ = new BehaviorSubject<string>(null);

  @ViewChild('varTemplate') template: TemplateRef<unknown>;
  @ViewChild(BdDialogComponent) dialog: BdDialogComponent;

  private subscription: Subscription;

  ngOnInit(): void {
    this.subscription = combineLatest([
      this.edit.node$.pipe(distinctUntilChanged()),
      this.edit.product$.pipe(distinctUntilChanged()),
      this.edit.applications$.pipe(distinctUntilChanged()),
      this.instanceEdit.nodes$,
    ]).subscribe(([node, prod, apps, nodeConfigs]) => {
      if (!node || !prod || !apps || !nodeConfigs) {
        this.records$.next([]);
        return;
      }

      this.clipBoardCfg$.next(null);

      const groups: AppGroup[] = [];
      const nodeConfig = nodeConfigs[node.nodeName];
      const sortedApps = [...apps].sort((a, b) => {
        const x = a.name.localeCompare(b.name);
        if (x !== 0) {
          return x;
        }

        return a.key.name.localeCompare(b.key.name);
      });

      for (const app of sortedApps) {
        // Filter for target OS.
        if (this.isClientNode(node)) {
          if (app.descriptor.type !== ApplicationType.CLIENT) {
            continue;
          }
        } else {
          if (app.descriptor.type !== ApplicationType.SERVER) {
            continue;
          }
          if (getAppOs(app.key) !== nodeConfig?.os) {
            continue;
          }
        }

        const baseKey = getAppKeyName(app.key);
        let group = groups.find((g) => g.appKeyName === baseKey);
        if (!group) {
          group = {
            appKeyName: baseKey,
            appDisplayName: app.name,
            applications: [],
            availableOs: [],
            templates: [],
          };
          groups.push(group);
        }

        group.applications.push(app);
        group.availableOs.push(getAppOs(app.key));
      }

      const recs: AppRow[] = [];
      for (const group of groups) {
        recs.push({ app: group, node: node, template: null });
        for (const tpl of prod.applicationTemplates) {
          if (`${prod.product}/${tpl.application}` === group.appKeyName) {
            recs.push({ app: group, node: node, template: tpl });
          }
        }
      }

      this.records$.next(recs);
      this.loading$.next(false);
    });

    this.subscription.add(
      combineLatest([this.edit.node$.pipe(distinctUntilChanged()), this.instanceEdit.nodes$, interval(1000)]).subscribe(
        ([node, nodeConfigs]) => this.readFromClipboard(node, nodeConfigs[node.nodeName]),
      ),
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  protected doPaste(cfg: ApplicationConfiguration) {
    this.edit.addProcessPaste(cfg);
  }

  private readFromClipboard(node: InstanceNodeConfigurationDto, minion: MinionDto) {
    this.clipBoardError$.next(null);

    if (!navigator.clipboard.readText) {
      // must be firefox. firefox allows reading the clipboard *only* from browser
      // extensions but never from web pages itself. it is rumored that there is a config
      // which can be enabled ("Dom.Events.Testing.AsynClipBoard"), however that did not
      // change browser behaviour in tests.
      this.clipBoardCfg$.next(null);
      this.clipBoardError$.next(
        'Clipboard access is not supported in this browser. Pasting applications is not possible.',
      );
      return;
    }

    // check clipboard content on init.
    const perm = 'clipboard-read' as PermissionName; // required due to TS bug.
    navigator.permissions.query({ name: perm }).then(
      (value: PermissionStatus) => {
        if (value.state !== 'granted') {
          // otherwise 'prompt' is open - not an error
          if (value.state === 'denied') {
            this.clipBoardCfg$.next(null);
            this.clipBoardError$.next('No permission to read from the clipboard, pasting not possible.');
          }
        }
      },
      (reason) => {
        this.clipBoardCfg$.next(null);
        this.clipBoardError$.next(`Cannot check clipboard permission (${reason}).`);
      },
    );

    // in ANY case we try to read, and ignore any error, since the attempt to read prompts the user for permission.
    navigator.clipboard.readText().then((data) => {
      let appConfig: ApplicationConfiguration = null;
      try {
        appConfig = JSON.parse(data) as ApplicationConfiguration;
      } catch (e) {
        this.clipBoardCfg$.next(null);
        return; // this is not an error, its just not an application configuration :)
      }

      // change OS if required
      if (!this.isClientNode(node)) {
        appConfig.application = updateAppOs(appConfig.application, minion.os);
      }

      this.edit.getApplication(appConfig.application.name).subscribe({
        next: (app) => {
          if (!app?.descriptor) {
            return; // app or descriptor not found -> product mismatch?
          }
          if (
            (app.descriptor.type === ApplicationType.SERVER && this.isClientNode(node)) ||
            (app.descriptor.type === ApplicationType.CLIENT && !this.isClientNode(node))
          ) {
            return; // not an error, just not suitable to paste
          }

          // raw, unprocessed.
          this.clipBoardCfg$.next(appConfig);
        },
        error: (err) => {
          console.log(`Error when reading clipboard: ${err}`);
          this.clipBoardCfg$.next(null);
        },
      });
    });
  }

  private isClientNode(node: InstanceNodeConfigurationDto) {
    return node.nodeName === CLIENT_NODE_NAME;
  }

  private addProcess(row: AppRow) {
    let vars: Observable<{ [key: string]: string }> = of({});
    if (!!row.template && !!row.template.templateVariables?.length) {
      this.response = {};
      for (const v of row.template.templateVariables) {
        this.response[v.id] = v.defaultValue;
      }
      this.selectedTemplate = row.template;

      vars = this.dialog.message({
        header: 'Assign Variable Values',
        template: this.template,
        validation: () => this.validateHasAllVariables(row.template, this.response),
        actions: [
          { name: 'Cancel', confirm: false, result: null },
          { name: 'Confirm', confirm: true, result: this.response },
        ],
      });
    }

    vars.subscribe((v) => {
      if (!v) {
        // we have an empty object in any accepted case, null if cancelled.
        return;
      }
      const allCreations: Observable<unknown>[] = [];
      if (this.isClientNode(row.node)) {
        // multiple applications for the same ID for multiple OS'.
        for (const app of row.app.applications) {
          allCreations.push(this.edit.addProcess(row.node, app, row.template, v, []));
        }
      } else {
        // only one application may exist with the same ID.
        allCreations.push(this.edit.addProcess(row.node, row.app.applications[0], row.template, v, []));
      }

      this.loading$.next(true);
      combineLatest(allCreations)
        .pipe(finalize(() => this.loading$.next(false)))
        .subscribe(() => {
          this.instanceEdit.conceal('Add ' + (row.template ? row.template.name : row.app.appDisplayName));
        });
    });
  }

  private validateHasAllVariables(
    template: FlattenedApplicationTemplateConfiguration,
    variables: { [key: string]: string },
  ) {
    for (const v of template.templateVariables) {
      if (!variables[v.id]) {
        return false;
      }
    }
    return true;
  }
}
