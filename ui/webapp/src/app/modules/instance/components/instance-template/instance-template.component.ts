import { Component, EventEmitter, Input, OnInit, Output, TemplateRef, ViewChild } from '@angular/core';
import { MatButtonToggleChange } from '@angular/material/button-toggle';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { cloneDeep } from 'lodash';
import { ApplicationGroup } from 'src/app/models/application.model';
import { CLIENT_NODE_NAME, EMPTY_INSTANCE_NODE_CONFIGURATION } from 'src/app/models/consts';
import { ApplicationConfiguration, ApplicationDto, ApplicationType, InstanceNodeConfigurationDto, InstanceTemplateApplication, InstanceTemplateDescriptor, InstanceTemplateNode, MinionDto, ProcessControlConfiguration, ProductDto } from 'src/app/models/gen.dtos';
import { ProcessConfigDto } from 'src/app/models/process.model';
import { Logger, LoggingService } from 'src/app/modules/core/services/logging.service';
import { ApplicationService } from '../../services/application.service';

@Component({
  selector: 'app-instance-template',
  templateUrl: './instance-template.component.html',
  styleUrls: ['./instance-template.component.css']
})
export class InstanceTemplateComponent implements OnInit {

  private readonly log: Logger = this.loggingService.getLogger('InstanceTemplateComponent');

  @Input()
  instanceGroupName: string;

  @Input()
  minionConfigs: { [ minionName: string ]: MinionDto } = {};

  @Input()
  product: ProductDto;

  @Output()
  templateApplied = new EventEmitter<any>();

  @ViewChild('dialog')
  template: TemplateRef<any>;

  config: ProcessConfigDto;
  selectedTemplate: InstanceTemplateDescriptor;
  allTemplates: InstanceTemplateDescriptor[];

  variables: {[key: string]: string};
  nodeMappings: string[];
  statusText = 'Applying template...';
  dialogRef: MatDialogRef<any>;

  constructor(private dialog: MatDialog, private appService: ApplicationService, private loggingService: LoggingService) { }

  ngOnInit(): void {
  }

  updateSelection(event: MatButtonToggleChange) {
    const index = event.value;
    this.selectedTemplate = this.allTemplates[index];

    this.nodeMappings = [];
    for (let i = 0; i < this.selectedTemplate.nodes.length; ++i) {
      const node = this.selectedTemplate.nodes[i];
      if (this.config.nodeList.nodeConfigDtos.find(inc => inc.nodeName === node.name)) {
        this.nodeMappings[i] = node.name;
      } else {
        if (node.type === ApplicationType.CLIENT) {
          this.nodeMappings[i] = CLIENT_NODE_NAME;
        } else {
          this.nodeMappings[i] = '__none';
        }
      }
    }

    this.variables = {};
    if (this.selectedTemplate?.variables?.length) {
      for (const variable of this.selectedTemplate.variables) {
        this.variables[variable.uid] = variable.defaultValue ? variable.defaultValue : '';
      }
    }
  }

  getNiceName(nodeName: string) {
    if (nodeName === CLIENT_NODE_NAME) {
      return 'Client Applications';
    }
    return nodeName;
  }

  getNodesForType(node: InstanceTemplateNode): string[] {
    if (node.type === ApplicationType.CLIENT) {
      return [CLIENT_NODE_NAME];
    } else {
      return this.config.nodeList.nodeConfigDtos.filter(n => n.nodeName !== CLIENT_NODE_NAME).map(n => n.nodeName);
    }
  }

  public fillFromTemplate(config: ProcessConfigDto, product: ProductDto) {
    this.selectedTemplate = null;
    this.allTemplates = product.templates;
    this.config = config;

    if (!this.allTemplates || !this.allTemplates.length || config.readonly) {
      return;
    }

    this.dialogRef = this.dialog.open(this.template, {
      width: '725px',
      disableClose: true,
      data: { }
    });

    this.dialogRef.afterClosed().subscribe(async result => {
      this.templateApplied.emit();
    });
  }

  async applyTemplate() {
    for (let i = 0; i < this.nodeMappings.length; ++i) {
      const mapping = this.nodeMappings[i];
      const nodeTemplate = this.selectedTemplate.nodes[i];

      if (mapping === '__none') {
        continue;
      }

      const physNode = this.config.nodeList.nodeConfigDtos.find(n => n.nodeName === mapping);
      if (!physNode.nodeConfiguration) {
        physNode.nodeConfiguration = cloneDeep(EMPTY_INSTANCE_NODE_CONFIGURATION);
        physNode.nodeConfiguration.uuid = this.config.instance.uuid;
        physNode.nodeConfiguration.name = this.config.instance.name;
        physNode.nodeConfiguration.autoStart = true;
      }

      for (const app of nodeTemplate.applications) {
        const appgroups: ApplicationGroup[] = nodeTemplate.type === ApplicationType.CLIENT ? this.config.clientApps : this.config.serverApps;
        const targetAppName = this.product.product + '/' + app.application;
        const appGroup = appgroups.find(grp => grp.appKeyName === targetAppName);

        if (!appGroup) {
          this.log.warn(`Cannot find application ${targetAppName} in ${JSON.stringify(appgroups)}`);
        }

        // create the according applications, applying existing global parameters or default values as required.
        if (physNode.nodeName === CLIENT_NODE_NAME) {
          for (const perOsApp of appGroup.applications) {
            const cfg = await this.appService.createNewAppConfig(this.instanceGroupName, this.config, perOsApp);
            this.applyApplicationTemplate(physNode, cfg, perOsApp, app);
          }
        } else if (this.minionConfigs[physNode.nodeName]?.os) {
          const appDesc = appGroup.getAppFor(this.minionConfigs[physNode.nodeName].os);
          const cfg = await this.appService.createNewAppConfig(this.instanceGroupName, this.config, appDesc);
          this.applyApplicationTemplate(physNode, cfg, appDesc, app);
        } else {
          this.log.error(`Cannot determin how to add application to node: ${targetAppName} to ${physNode.nodeName}`);
        }
      }
    }
    this.statusText = null;
  }

  private applyApplicationTemplate(node: InstanceNodeConfigurationDto, app: ApplicationConfiguration, desc: ApplicationDto, templ: InstanceTemplateApplication) {
    if (templ.name) {
      app.name = templ.name;
    }
    if (templ.processControl) {
      // partially deserialized - only apply specified attributes.
      const pc = templ.processControl as ProcessControlConfiguration;
      if (pc.attachStdin !== undefined) { app.processControl.attachStdin = pc.attachStdin; }
      if (pc.gracePeriod !== undefined) { app.processControl.gracePeriod = pc.gracePeriod; }
      if (pc.keepAlive !== undefined) { app.processControl.keepAlive = pc.keepAlive; }
      if (pc.noOfRetries !== undefined) { app.processControl.noOfRetries = pc.noOfRetries; }
      if (pc.startType !== undefined) { app.processControl.startType = pc.startType; }
    }

    for (const param of templ.startParameters) {
      const paramDesc = desc.descriptor.startCommand.parameters.find(p => p.uid === param.uid);
      if (!paramDesc) {
        this.log.warn(`Cannot find parameter ${param.uid} in application ${desc.name}`);
        continue;
      }

      // find or create parameter if not there yet.
      let paramCfg = app.start.parameters.find(p => p.uid === param.uid);
      if (!paramCfg) {
        paramCfg = this.appService.createParameter(paramDesc, this.appService.getAllApps(this.config));
        app.start.parameters.push(paramCfg); // order is corrected later on.
      }

      if (param.value) {
        paramCfg.value = param.value;

        if (paramCfg.value.indexOf('{{T:') !== -1) {
          let found = true;
          while (found) {
            const rex = new RegExp('{{T:([^}]*)}}').exec(paramCfg.value);
            if (rex) {
              paramCfg.value = paramCfg.value.replace(rex[0], this.expandVar(rex[1]));
            } else {
              found = false;
            }
          }
        }

        paramCfg.preRendered = this.appService.preRenderParameter(paramDesc, paramCfg.value);
      }
    }

    this.updateParameterOrder(app, desc);
    node.nodeConfiguration.applications.push(app);

    // if this application set a global parameter, apply to all others.
    this.appService.updateGlobalParameters(desc.descriptor, app, this.appService.getAllApps(this.config));
  }

  expandVar(variable: string): string {
    let varName = variable;
    const colIndex = varName.indexOf(':');
    if (colIndex !== -1) {
      varName = varName.substr(0, colIndex);
    }
    const val = this.variables[varName];

    if (colIndex !== -1) {
      const op = variable.substr(colIndex + 1);
      const opNum = Number(op);
      const valNum = Number(val);

      if (Number.isNaN(opNum) || Number.isNaN(valNum)) {
        this.log.error(`Invalid variable substitution for ${variable}: '${op}' or '${val}' is not a number.`);
        return variable;
      }
      return (valNum + opNum).toString();
    }

    return val;
  }

  updateParameterOrder(app: ApplicationConfiguration, desc: ApplicationDto) {
    const params = app.start.parameters;
    const descs = desc.descriptor.startCommand.parameters;

    // there can be no custom parameters in templates, so no need to care of them. only parameters for which descriptors exist
    // can be there.
    params.sort((a, b) => {
      return descs.findIndex(p => a.uid === p.uid) - descs.findIndex(p => b.uid === p.uid);
    });
  }

}
