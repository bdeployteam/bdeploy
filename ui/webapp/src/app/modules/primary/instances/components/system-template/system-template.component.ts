import { StepperSelectionEvent } from '@angular/cdk/stepper';
import { Component, OnInit, ViewChild, ViewEncapsulation, inject } from '@angular/core';
import { MatStepper } from '@angular/material/stepper';
import { Observable, map } from 'rxjs';
import { StatusMessage } from 'src/app/models/config.model';
import { CLIENT_NODE_NAME } from 'src/app/models/consts';
import { BdDataColumn } from 'src/app/models/data';
import { BdDataIconCellComponent } from 'src/app/modules/core/components/bd-data-icon-cell/bd-data-icon-cell.component';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { UploadState, UploadStatus } from 'src/app/modules/core/services/upload.service';
import { performTemplateVariableSubst } from 'src/app/modules/core/utils/object.utils';
import { ServersService } from '../../../servers/services/servers.service';
import { SystemsService } from '../../../systems/services/systems.service';
import {
  ApplicationType,
  FlattenedInstanceTemplateConfiguration,
  FlattenedInstanceTemplateGroupConfiguration,
  InstancePurpose,
  InstanceTemplateReferenceDescriptor,
  InstanceTemplateReferenceResultDto,
  InstanceTemplateReferenceStatus,
  ManagedMasterDto,
  ProductDto,
  SystemTemplateDto,
  SystemTemplateRequestDto,
  SystemTemplateResultDto,
  TemplateVariable,
} from './../../../../../models/gen.dtos';
import { GroupsService } from './../../../groups/services/groups.service';

export class TemplateSelection {
  public ref: InstanceTemplateReferenceDescriptor;
  public product: ProductDto;
  public tpl: FlattenedInstanceTemplateConfiguration;

  public expandedName: string;
  public expandedDescription: string;

  public groups: { [key: string]: string };
  public nodeNames: { [key: string]: string[] };
  public nodeLabels: { [key: string]: string[] };

  public isApplyInstance: boolean;
  public isAnyGroupSelected: boolean;

  public variables: { [key: string]: string };
  public requiredVariables: TemplateVariable[] = [];
  public isAllVariablesSet: boolean;
}

const colInstanceName: BdDataColumn<InstanceTemplateReferenceResultDto> = {
  id: 'name',
  name: 'Instance',
  data: (r) => r.name,
  width: '200px',
};

const colInstResIcon: BdDataColumn<InstanceTemplateReferenceResultDto> = {
  id: 'status',
  name: 'Status',
  data: (r) =>
    r.status === InstanceTemplateReferenceStatus.OK
      ? 'check'
      : r.status === InstanceTemplateReferenceStatus.ERROR
        ? 'error'
        : 'warning',
  component: BdDataIconCellComponent,
  width: '50px',
};

const colInstanceMsg: BdDataColumn<InstanceTemplateReferenceResultDto> = {
  id: 'msg',
  name: 'Message',
  data: (r) => r.message,
};

@Component({
  selector: 'app-system-template',
  templateUrl: './system-template.component.html',
  styleUrls: ['./system-template.component.css'],
  encapsulation: ViewEncapsulation.None,
})
export class SystemTemplateComponent implements OnInit {
  protected cfg = inject(ConfigService);
  protected groups = inject(GroupsService);
  protected systems = inject(SystemsService);
  protected servers = inject(ServersService);

  protected file: File;
  protected template: SystemTemplateDto;
  protected name: string;
  protected purpose: InstancePurpose;
  protected serverSelectionCompleted: boolean;
  protected isCentral: boolean;
  protected serverDtos: ManagedMasterDto[];
  protected serverLabels: string[];
  protected selectedServer: ManagedMasterDto;
  protected templates: TemplateSelection[];

  protected systemVariables: { [key: string]: string };
  protected requiredSystemVariables: TemplateVariable[] = [];
  protected isAllSystemVariablesSet = false;

  protected nodeNames: string[];
  protected isAllTemplateGroupsSelected = false;
  protected isAllVariablesSet = false;
  protected isAnyInstanceApplied = false;
  protected result: SystemTemplateResultDto;
  protected resultCols: BdDataColumn<InstanceTemplateReferenceResultDto>[] = [
    colInstResIcon,
    colInstanceName,
    colInstanceMsg,
  ];
  protected resultIsSuccess: boolean;
  protected resultHasWarnings: boolean;
  protected purposes: InstancePurpose[] = [
    InstancePurpose.PRODUCTIVE,
    InstancePurpose.DEVELOPMENT,
    InstancePurpose.TEST,
  ];

  protected systemNames$: Observable<string[]>;

  protected onUploadResult: (status: UploadStatus) => string = (s) => {
    if (s.state === UploadState.FAILED) {
      return s.detail as string;
    }

    this.template = s.detail as SystemTemplateDto;

    return `Loaded '${this.template.template.name}', will create ${this.template.template.instances?.length} instances.`;
  };

  @ViewChild(MatStepper) private stepper: MatStepper;

  ngOnInit() {
    this.systemNames$ = this.systems.systems$.pipe(map((s) => s.map((x) => x.config.name)));

    this.cfg.isCentral$.subscribe((b) => {
      this.isCentral = b;
      this.serverSelectionCompleted = !this.isCentral;
    });

    this.servers.servers$.subscribe((s) => {
      if (!s?.length) {
        this.serverDtos = null;
        this.serverLabels = null;
        return;
      }

      this.serverDtos = s;
      this.serverLabels = s.map((x) => `${x.hostName} - ${x.description}`);
    });
  }

  protected readSystemTemplate(file: File) {
    this.file = file;
  }

  protected onDismiss() {
    this.template = null;
    this.file = null;
  }

  protected onStepSelectionChange(event: StepperSelectionEvent) {
    switch (event.selectedIndex) {
      case 0:
        this.onChooseTargetStep();
        break;
      case 1:
        this.stepper.steps.get(0).editable = false; // no back.
        this.onSelectTemplateStep();
        break;
      case 2:
        this.stepper.steps.get(1).editable = false; // no back.
        this.onSelectNameAndPurposeStep();
        break;
      case 3:
        this.stepper.steps.get(2).editable = false; // no back.
        this.onQuerySystemTemplateVariablesStep();
        break;
      case 4:
        this.stepper.steps.get(3).editable = false; // no back.
        this.onConfigureInstanceTemplatesStep();
        break;
      case 5:
        this.onApplyStep();
        break;
    }
  }

  private onApplyStep() {
    this.stepper.steps.forEach((s) => (s.editable = false));

    {
      const data: SystemTemplateRequestDto = {
        name: this.name,
        purpose: this.purpose,
        minion: this.selectedServer?.hostName,
        template: this.template.template,
        groupMappings: this.templates.map((t) => ({
          instanceName: t.expandedName,
          productKey: t.product.key,
          groupToNode: t.groups,
          templateVariableValues: t.variables,
        })),
        templateVariableValues: this.systemVariables,
      };

      this.systems.apply(data).subscribe((resultDto) => {
        this.result = resultDto;
        this.resultIsSuccess =
          resultDto.results.map((r) => r.status).findIndex((s) => s === InstanceTemplateReferenceStatus.ERROR) === -1;
        this.resultHasWarnings =
          resultDto.results.map((r) => r.status).findIndex((s) => s === InstanceTemplateReferenceStatus.WARNING) !== -1;
        this.stepper.next();
      });
    }
  }

  private onQuerySystemTemplateVariablesStep() {
    this.requiredSystemVariables = [];
    this.systemVariables = {};
    this.isAllSystemVariablesSet = false;

    if (this.template.template.templateVariables?.length) {
      // template variables applicable for system variables.
      this.requiredSystemVariables.push(...this.template.template.templateVariables);
    }

    if (this.requiredSystemVariables.length) {
      for (const v of this.requiredSystemVariables) {
        this.systemVariables[v.id] = v.defaultValue;
      }
    }

    this.validateHasAllSystemVariables();
  }

  private onConfigureInstanceTemplatesStep() {
    this.isAllTemplateGroupsSelected = false;
    this.nodeNames = Object.keys(this.template.nodes);
    this.templates = this.template.template.instances.map((i) => {
      // cannot be null, as the backend would otherwise reject.
      const prod = this.template.products.find(
        (p) =>
          p.product === i.productId && (!i.productVersionRegex || new RegExp(i.productVersionRegex).test(p.key.tag)),
      );

      const expStatus: StatusMessage[] = [];
      const expName = performTemplateVariableSubst(i.name, this.systemVariables, expStatus);
      const expDesc = performTemplateVariableSubst(i.description, this.systemVariables, expStatus);

      const tpl = prod.instanceTemplates.find((t) => t.name === i.templateName);
      const groups = {};
      const nodes: { [key: string]: string[] } = {};
      const nodeLabels = {};

      for (const grp of tpl.groups) {
        nodes[grp.name] = this.getNodesFor(grp);
        nodeLabels[grp.name] = this.getLabelsFor(grp);
        groups[grp.name] = null; // not selected but defined :)
        const mapping = i.defaultMappings?.find((d) => d.group === grp.name);
        if (mapping) {
          // the mapping can use system template variables!
          const expNode = performTemplateVariableSubst(mapping.node, this.systemVariables, expStatus);

          const presetNode = nodes[grp.name].find((n) => n === expNode);
          if (!presetNode) {
            console.log(`Cannot find node to preset for ${grp.name}: ${expNode}`);
          } else {
            groups[grp.name] = presetNode;
          }
        }
      }

      if (expStatus.length > 0) {
        console.log(`There have been ${expStatus.length} issues when expanding template variables for ${i.name}`);
        expStatus.forEach((s) => console.log(` -> ${s.message}`));
      }

      const result: TemplateSelection = {
        ref: i,
        product: prod,
        tpl: tpl,
        expandedName: expName,
        expandedDescription: expDesc,
        groups: groups,
        nodeNames: nodes,
        nodeLabels: nodeLabels,
        isApplyInstance: true,
        isAnyGroupSelected: false,
        variables: {},
        requiredVariables: [],
        isAllVariablesSet: false,
      };

      return result;
    });

    // once re-calculate after all templates are finally defined.
    this.templates.forEach((t) => this.validateAnyGroupSelected(t));
    this.validateAllTemplateGroupsSelected();
  }

  private updateInstanteTemplateVariables(tpl: TemplateSelection) {
    tpl.requiredVariables = [];
    if (tpl.tpl.directlyUsedTemplateVars?.length) {
      tpl.requiredVariables.push(...tpl.tpl.directlyUsedTemplateVars);
    }

    for (const grp of tpl.tpl.groups) {
      if (tpl.groups[grp.name] === null) {
        // not selected;
        continue;
      }

      tpl.tpl.groups
        .flatMap((g) => g.groupVariables)
        .forEach((v) => {
          if (tpl.requiredVariables.findIndex((x) => x.id === v.id) === -1) {
            tpl.requiredVariables.push(v);
          }
        });
    }

    // according to the "new" requiredVariables list, clear out properties which should not be there (anymore)
    for (const varName of Object.keys(tpl.variables)) {
      if (tpl.requiredVariables.findIndex((x) => x.id === varName) === -1) {
        delete tpl.variables[varName];
      }
    }

    if (tpl.ref.fixedVariables?.length) {
      // handle fixed variables passed on from system template.
      for (const fixed of tpl.ref.fixedVariables) {
        const reqIndex = tpl.requiredVariables.findIndex((x) => x.id === fixed.id);
        if (reqIndex === -1) {
          continue; // not required in this instance template
        }
        // SET in variables, UNSET in requiredVariables (no longer queried from user)
        tpl.variables[fixed.id] = fixed.value;
        tpl.requiredVariables.splice(reqIndex, 1);
      }
    }

    // set default values where undefined.
    if (tpl.requiredVariables.length) {
      for (const v of tpl.requiredVariables) {
        if (tpl.variables[v.id] === undefined) {
          tpl.variables[v.id] = v.defaultValue;
        }
      }
    }

    this.validateHasAllVariables(tpl);
  }

  private onSelectNameAndPurposeStep() {
    this.name = this.template?.template?.name;
  }

  private onSelectTemplateStep() {
    this.template = null;
    this.file = null;
  }

  private onChooseTargetStep() {
    this.serverSelectionCompleted = !this.isCentral;
    this.selectedServer = null;
  }

  private getNodesFor(group: FlattenedInstanceTemplateGroupConfiguration): string[] {
    if (group.type === ApplicationType.CLIENT) {
      return [null, CLIENT_NODE_NAME];
    } else {
      return [
        null,
        // eslint-disable-next-line no-unsafe-optional-chaining
        ...this.nodeNames.filter((n) => n !== CLIENT_NODE_NAME),
      ];
    }
  }

  private getLabelsFor(group: FlattenedInstanceTemplateGroupConfiguration): string[] {
    const nodeValues = this.getNodesFor(group);

    return nodeValues.map((n) => {
      if (n === null) {
        return '(skip)';
      } else if (n === CLIENT_NODE_NAME) {
        return 'Apply to Client Applications';
      } else {
        return 'Apply to ' + n;
      }
    });
  }

  private validateAllTemplateGroupsSelected() {
    if (!this.templates?.length) {
      this.isAllTemplateGroupsSelected = false;
      return;
    }

    for (const tpl of this.templates) {
      if (tpl.isApplyInstance && !tpl.isAnyGroupSelected) {
        this.isAllTemplateGroupsSelected = false;
        return;
      }
    }
    this.isAllTemplateGroupsSelected = true;
    this.isAnyInstanceApplied = !this.templates.every((t) => !t.isApplyInstance);
  }

  protected validateAnyGroupSelected(tpl: TemplateSelection) {
    // we may need more or less variables :) do this early for the early return below.
    this.updateInstanteTemplateVariables(tpl);

    for (const grp of tpl.tpl.groups) {
      const v = tpl.groups[grp.name];
      if (v !== null && v !== undefined) {
        tpl.isAnyGroupSelected = true;
        this.validateAllTemplateGroupsSelected();
        return;
      }
    }
    tpl.isAnyGroupSelected = false;
    this.validateAllTemplateGroupsSelected();
  }

  protected validateHasAllSystemVariables() {
    if (!this.template) {
      return;
    }

    for (const v of this.requiredSystemVariables) {
      const value = this.systemVariables[v.id];
      if (value === '' || value === null || value === undefined) {
        this.isAllSystemVariablesSet = false;
        return;
      }
    }
    this.isAllSystemVariablesSet = true;
  }

  protected validateHasAllVariables(tpl: TemplateSelection) {
    if (!this.template) {
      return;
    }

    tpl.isAllVariablesSet = true;
    if (tpl.isApplyInstance) {
      for (const v of tpl.requiredVariables) {
        const value = tpl.variables[v.id];
        if (value === '' || value === null || value === undefined) {
          tpl.isAllVariablesSet = false;
        }
      }
    }

    // maybe undefined while init.
    if (this.templates?.length) {
      this.isAllVariablesSet = this.templates.every((t) => t.isAllVariablesSet);
    }
  }

  protected toggleSkipInstance(val: boolean, tpl: TemplateSelection) {
    if (!val) {
      tpl.groups = {};
    }

    // update states.
    this.validateHasAllVariables(tpl);
    this.validateAnyGroupSelected(tpl);
  }
}
