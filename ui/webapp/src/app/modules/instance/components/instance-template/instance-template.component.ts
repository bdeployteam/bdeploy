import { Component, EventEmitter, Input, OnInit, Output, TemplateRef, ViewChild } from '@angular/core';
import { MatButtonToggleChange } from '@angular/material/button-toggle';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { cloneDeep } from 'lodash-es';
import { ApplicationGroup } from 'src/app/models/application.model';
import { StatusMessage } from 'src/app/models/config.model';
import { CLIENT_NODE_NAME, EMPTY_INSTANCE_NODE_CONFIGURATION } from 'src/app/models/consts';
import {
  ApplicationType,
  InstanceTemplateDescriptor,
  InstanceTemplateGroup,
  MinionDto,
  ProductDto,
} from 'src/app/models/gen.dtos';
import { ProcessConfigDto } from 'src/app/models/process.model';
import { ApplicationService } from '../../services/application.service';

@Component({
  selector: 'app-instance-template',
  templateUrl: './instance-template.component.html',
  styleUrls: ['./instance-template.component.css'],
})
export class InstanceTemplateComponent implements OnInit {
  @Input()
  instanceGroupName: string;

  @Input()
  minionConfigs: { [minionName: string]: MinionDto } = {};

  @Input()
  product: ProductDto;

  @Output()
  templateApplied = new EventEmitter<any>();

  @ViewChild('dialog')
  template: TemplateRef<any>;

  config: ProcessConfigDto;
  selectedTemplate: InstanceTemplateDescriptor;
  allTemplates: InstanceTemplateDescriptor[];

  variables: { [key: string]: string };
  nodeMappings: string[];
  messages: StatusMessage[][][] = [];
  applyDone = false;
  dialogRef: MatDialogRef<any>;

  constructor(private dialog: MatDialog, private appService: ApplicationService) {}

  ngOnInit(): void {}

  updateSelection(event: MatButtonToggleChange) {
    const index = event.value;
    this.selectedTemplate = this.allTemplates[index];

    this.nodeMappings = [];
    for (let i = 0; i < this.selectedTemplate.groups.length; ++i) {
      this.nodeMappings[i] = '__none';
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

  getNodesForType(group: InstanceTemplateGroup): string[] {
    if (group.type === ApplicationType.CLIENT) {
      return [CLIENT_NODE_NAME];
    } else {
      return this.config.nodeList.nodeConfigDtos.filter((n) => n.nodeName !== CLIENT_NODE_NAME).map((n) => n.nodeName);
    }
  }

  public fillFromTemplate(config: ProcessConfigDto, product: ProductDto) {
    this.selectedTemplate = null;
    this.allTemplates = product.instanceTemplates;
    this.config = config;

    if (!this.allTemplates || !this.allTemplates.length || config.readonly) {
      return;
    }

    this.dialogRef = this.dialog.open(this.template, {
      width: '725px',
      disableClose: true,
      data: {},
    });

    this.dialogRef.afterClosed().subscribe(async (result) => {
      this.templateApplied.emit();
    });
  }

  isNodeMappingSelected() {
    if (!this.nodeMappings.filter((m) => m !== '__none')?.length) {
      return false;
    }
    return true;
  }

  async applyTemplate() {
    for (let i = 0; i < this.nodeMappings.length; ++i) {
      const mapping = this.nodeMappings[i];
      const nodeTemplate = this.selectedTemplate.groups[i];

      const nodeStatus: StatusMessage[][] = [];
      this.messages.push(nodeStatus);

      if (mapping === '__none') {
        continue;
      }

      const physNode = this.config.nodeList.nodeConfigDtos.find((n) => n.nodeName === mapping);
      if (!physNode.nodeConfiguration) {
        physNode.nodeConfiguration = cloneDeep(EMPTY_INSTANCE_NODE_CONFIGURATION);
        physNode.nodeConfiguration.uuid = this.config.instance.uuid;
        physNode.nodeConfiguration.name = this.config.instance.name;
        physNode.nodeConfiguration.autoStart = true;
      }

      for (const app of nodeTemplate.applications) {
        const appStatus: StatusMessage[] = [];
        nodeStatus.push(appStatus);

        const type = nodeTemplate.type === ApplicationType.CLIENT ? ApplicationType.CLIENT : ApplicationType.SERVER;
        const appgroups: ApplicationGroup[] =
          type === ApplicationType.CLIENT ? this.config.clientApps : this.config.serverApps;
        const targetAppName = this.product.product + '/' + app.application;
        const appGroup = appgroups.find((grp) => grp.appKeyName === targetAppName);

        if (!appGroup) {
          appStatus.push({
            icon: 'error',
            message: `Cannot find application ${targetAppName} in product for a node of type ${type}. This is an error in the template.`,
          });
          continue;
        }

        // create the according applications, applying existing global parameters or default values as required.
        if (physNode.nodeName === CLIENT_NODE_NAME) {
          for (const perOsApp of appGroup.applications) {
            const cfg = await this.appService.createNewAppConfig(this.instanceGroupName, this.config, perOsApp);
            this.appService.applyApplicationTemplate(
              this.config,
              physNode,
              cfg,
              perOsApp,
              app,
              this.variables,
              appStatus
            );
            physNode.nodeConfiguration.applications.push(cfg);
          }
        } else if (this.minionConfigs[physNode.nodeName]?.os) {
          const appDesc = appGroup.getAppFor(this.minionConfigs[physNode.nodeName].os);
          if (appDesc) {
            const cfg = await this.appService.createNewAppConfig(this.instanceGroupName, this.config, appDesc);
            this.appService.applyApplicationTemplate(
              this.config,
              physNode,
              cfg,
              appDesc,
              app,
              this.variables,
              appStatus
            );
            physNode.nodeConfiguration.applications.push(cfg);
          } else {
            appStatus.push({
              icon: 'warning',
              message:
                'Cannot find application ' +
                appGroup.appName +
                ' for target node OS: ' +
                this.getNiceName(physNode.nodeName),
            });
          }
        } else {
          appStatus.push({
            icon: 'error',
            message: `Cannot determin how to add application to node: ${targetAppName} to ${physNode.nodeName}`,
          });
        }
      }
    }
    this.applyDone = true;
  }

  hasStatusForEachProcess(messages: StatusMessage[][], group: InstanceTemplateGroup) {
    if (!messages) {
      return false;
    }
    if (messages?.length < group.applications.length) {
      return false;
    }
    for (const msgs of messages) {
      if (!msgs?.length) {
        return false;
      }
    }
    return true;
  }
}
