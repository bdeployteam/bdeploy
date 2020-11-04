import { moveItemInArray } from '@angular/cdk/drag-drop';
import { Component, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output, TemplateRef, ViewChild } from '@angular/core';
import { MatBottomSheet, MatBottomSheetRef } from '@angular/material/bottom-sheet';
import { MatDialog } from '@angular/material/dialog';
import { format } from 'date-fns';
import { cloneDeep } from 'lodash-es';
import { DragulaService } from 'ng2-dragula';
import { Subscription } from 'rxjs';
import { StatusMessage } from 'src/app/models/config.model';
import { Logger, LoggingService } from 'src/app/modules/core/services/logging.service';
import { MessageBoxMode } from 'src/app/modules/shared/components/messagebox/messagebox.component';
import { MessageboxService } from 'src/app/modules/shared/services/messagebox.service';
import { convert2String } from 'src/app/modules/shared/utils/version.utils';
import { ApplicationGroup } from '../../../../models/application.model';
import { CLIENT_NODE_NAME, EMPTY_INSTANCE_NODE_CONFIGURATION } from '../../../../models/consts';
import { EventWithCallback } from '../../../../models/event';
import { ApplicationConfiguration, ApplicationDto, ApplicationTemplateDescriptor, ApplicationType, InstanceNodeConfigurationDto, MinionDto, MinionStatusDto, ProductDto } from '../../../../models/gen.dtos';
import { EditAppConfigContext, ProcessConfigDto } from '../../../../models/process.model';
import { getAppOs, updateAppOs } from '../../../shared/utils/manifest.utils';
import { ApplicationService } from '../../services/application.service';
import { ApplicationTemplateVariableDialogComponent, VariableInput } from '../application-template-variable-dialog/application-template-variable-dialog.component';


@Component({
  selector: 'app-instance-node-card',
  templateUrl: './instance-node-card.component.html',
  styleUrls: ['./instance-node-card.component.css'],
})
export class InstanceNodeCardComponent implements OnInit, OnDestroy {
  /* CSS Classes attached to nodes to highligh valid / invalid drop zones */
  private readonly VALID_DROP_ZONE_CLASS = 'instance-node-valid-drop-zone';
  private readonly INVALID_DROP_ZONE_CLASS = 'instance-node-invalid-drop-zone';
  private readonly CURRENT_DRAG_CONTAINER_CLASS = 'current-drag-container';

  private log: Logger = this.loggingService.getLogger('InstanceNodeCardComponent');

  @Input() instanceGroupName: string;
  @Input() activatedInstanceTag: string;
  @Input() processConfig: ProcessConfigDto;
  @Input() productMissing: boolean;
  @Input() node: InstanceNodeConfigurationDto;
  @Input() manageApplications: boolean;
  @Input() isReadonly: boolean;
  @Input() isInstanceDirty: boolean;
  @Input() minionConfig: MinionDto;
  @Input() minionState: MinionStatusDto;
  @Input() productTags: ProductDto[];
  @Input() selectedProcess: ApplicationConfiguration;

  @Output() editAppConfigEvent = new EventEmitter<EditAppConfigContext>();
  @Output() editAppEndpointsEvent = new EventEmitter<EditAppConfigContext>();
  @Output() editNodeAppsEvent = new EventEmitter<void>();
  @Output() removeNodeAppEvent = new EventEmitter<ApplicationConfiguration>();
  @Output() selectAppConfigEvent = new EventEmitter<ApplicationConfiguration>();
  @Output() downloadClickAndStartEvent = new EventEmitter<ApplicationConfiguration>();
  @Output() downloadInstallerEvent = new EventEmitter<EventWithCallback<ApplicationConfiguration>>();

  @ViewChild('appNodeCard', { read: ElementRef })
  appNodeCard: ElementRef;

  @ViewChild('dragulaContainer', { read: ElementRef })
  dragulaContainer: ElementRef;

  private nodeConfigCreated = false;
  private subscription: Subscription;

  public nodeApps: ApplicationConfiguration[] = [];

  bottomSheet: MatBottomSheetRef<any>;

  constructor(
    private appService: ApplicationService,
    private dragulaService: DragulaService,
    private mbService: MessageboxService,
    private loggingService: LoggingService,
    private dialog: MatDialog,
    private bottomSheetSvc: MatBottomSheet
    ) {}

  ngOnInit() {
    this.subscription = new Subscription();
    this.nodeApps = this.node.nodeConfiguration ? this.node.nodeConfiguration.applications : [];

    // Handle dropping of applications
    this.subscription.add(
      this.dragulaService.dropModel().subscribe(({ target, source, item, sourceIndex, targetIndex }) => {
        this.onDrop(target, source, item, sourceIndex, targetIndex);
      }),
    );

    // Visualize valid targets for dropping elements
    this.subscription.add(
      this.dragulaService.drag().subscribe(({ el }) => {
        this.onDrag(el);
      }),
    );

    // Remove visualization of valid targets for dropping elements
    this.subscription.add(
      this.dragulaService.dragend().subscribe(_ => {
        this.onDragEnd();
      }),
    );

    this.subscription.add(
      this.dragulaService.over().subscribe(({ container }) => {
        this.onDragOver(container);
      }),
    );

    this.subscription.add(
      this.dragulaService.out().subscribe(({ container }) => {
        this.onDragOut(container);
      }),
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /** Called when the drag ended (canceled or success) */
  private onDragEnd() {
    const cardClassList = this.appNodeCard.nativeElement.classList;
    cardClassList.remove(this.VALID_DROP_ZONE_CLASS);
    cardClassList.remove(this.INVALID_DROP_ZONE_CLASS);
    cardClassList.remove(this.CURRENT_DRAG_CONTAINER_CLASS);
  }

  /** Called when a drag operation started */
  private onDrag(el: Element) {
    const cardClassList = this.appNodeCard.nativeElement.classList;
    const dropContainer = this.dragulaContainer.nativeElement;
    if (this.appService.isAppCompatibleWithNode(el, dropContainer)) {
      cardClassList.remove(this.INVALID_DROP_ZONE_CLASS);
      cardClassList.add(this.VALID_DROP_ZONE_CLASS);
    } else {
      cardClassList.remove(this.VALID_DROP_ZONE_CLASS);
      cardClassList.add(this.INVALID_DROP_ZONE_CLASS);
    }
  }

  private onDragOver(target: Element) {
    const id = 'dragula-nodeName-' + this.node.nodeName;
    const isThisNodeTheTarget = target.className.includes(id);
    if (isThisNodeTheTarget) {
      this.appNodeCard.nativeElement.classList.add(this.CURRENT_DRAG_CONTAINER_CLASS);
    }
  }

  private onDragOut(target: Element) {
    const id = 'dragula-nodeName-' + this.node.nodeName;
    const isThisNodeTheTarget = target.className.includes(id);
    if (isThisNodeTheTarget) {
      this.appNodeCard.nativeElement.classList.remove(this.CURRENT_DRAG_CONTAINER_CLASS);
    }
  }

  /** Called when an application is dropped */
  private onDrop(target: Element, source: Element, item: any, sourceIndex: number, targetIndex: number) {
    const id = 'dragula-nodeName-' + this.node.nodeName;
    const isThisNodeTheTarget = target.className.includes(id);
    const isThisNodeTheSource = source.className.includes(id);
    if (isThisNodeTheSource && isThisNodeTheTarget) {
      // Re-Arrange within the same node
      this.moveProcess(item, sourceIndex, targetIndex);
    } else if (isThisNodeTheTarget) {
      // Add process if we are the target
      this.addProcess(item, targetIndex);
    } else if (isThisNodeTheSource) {
      // Remove process if we are the source
      this.removeProcess(item, sourceIndex);
    }
  }

  /**
   * Called when the user re-arranges an existing application within the same node
   */
  private moveProcess(data: any, sourceIndex: number, targetIndex: number) {
    // Move within the array
    moveItemInArray(this.nodeApps, sourceIndex, targetIndex);

    // Notify about the change
    this.editNodeAppsEvent.emit();
  }

  getProductOfInstance(pcd: ProcessConfigDto): ProductDto {
    return this.productTags.find(
      p => p.key.name === pcd.instance.product.name && p.key.tag === pcd.instance.product.tag,
    );
  }

  getApplicationTemplate(id: string, pcd: ProcessConfigDto): ApplicationTemplateDescriptor {
    const prod = this.getProductOfInstance(pcd);
    if (!prod) {
      return null;
    }
    return prod.applicationTemplates.find(t => t.id === id);
  }

  async applyApplicationTemplate(id: string, cfg: ApplicationConfiguration, app: ApplicationDto): Promise<boolean> {
    if (!id) {
      return true; // that's ok
    }
    const tpl = this.getApplicationTemplate(id, this.processConfig);

    if (!tpl) {
      this.mbService.open({title: 'Cannot apply Template', message: `Cannot find template with id ${id} for application ${cfg.application.name}`, mode: MessageBoxMode.ERROR});
      return false; // not found
    }

    const tplVars = {};
    if (tpl.variables?.length) {
      const vars: VariableInput[] = [];
      for (const variable of tpl.variables) {
        vars.push({
          uid: variable.uid,
          name: variable.name,
          description: variable.description,
          value: variable.defaultValue,
          suggestedValues: variable.suggestedValues ? variable.suggestedValues : []
        });
      }

      const result = await this.dialog.open(ApplicationTemplateVariableDialogComponent, {
        width: '600px',
        data: vars
      }).afterClosed().toPromise();

      if (!result) {
        this.mbService.open({title: 'Cannot apply Template', message: `Cannot apply template, missing template variable input.`, mode: MessageBoxMode.ERROR});
        return false;
      }

      for (const r of result) {
        tplVars[r.uid] = r.value;
      }
    }

    const msgs: StatusMessage[] = [];
    this.appService.applyApplicationTemplate(this.processConfig, this.node, cfg, app, tpl, tplVars, msgs);
    const errors = [];
    for (const msg of msgs) {
      if (msg.icon === 'warning') {
        this.log.warn(msg.message);
      }
      if (msg.icon === 'error') {
        this.log.error(msg.message);
        errors.push(msg);
      }
    }
    if (errors.length) {
      this.mbService.open({title: 'Cannot apply Template', message: `There have been ${errors.length} errors. See console for details.`, mode: MessageBoxMode.ERROR});
      return false;
    }
    return true; // yay!
  }

  /**
   * Called when the user drops an existing application on this node or
   * when the user drops a new application group from the sidebar or
   * when a user pastes an application
   */
  private async addProcess(data: any, targetIndex: number) {
    if (!this.node.nodeConfiguration) {
      this.createNewNodeConfig();
    }

    // Create configurations for all supported OS
    if (data instanceof ApplicationGroup) {
      if (this.isClientApplicationsNode()) {
        for (const app of data.applications) {
          const cfg = await this.appService.createNewAppConfig(this.instanceGroupName, this.processConfig, app);
          const result = await this.applyApplicationTemplate(data.selectedTemplate, cfg, app);
          if (result) {
            this.nodeApps.splice(targetIndex, 0, cfg);
            this.editNodeAppsEvent.emit();
          }
        }
      } else {
        const nodeOs = this.minionConfig.os;
        const app = data.getAppFor(nodeOs);
        this.appService.createNewAppConfig(this.instanceGroupName, this.processConfig, app).then(cfg => {
          this.applyApplicationTemplate(data.selectedTemplate, cfg, app).then(r => {
            if (r) {
              this.nodeApps.splice(targetIndex, 0, cfg);
              this.editNodeAppsEvent.emit();
            }
          });
        });
      }
    } else {
      // Simply add the given app to our list of applications
      this.nodeApps.splice(targetIndex, 0, data);

      // Notify about the change
      this.editNodeAppsEvent.emit();
    }
  }

  /**
   * Called when the user drags the application from one node to another node.
   */
  removeProcess(application: ApplicationConfiguration, sourceIndex: number) {
    const index = this.nodeApps.indexOf(application);
    if (index === -1) {
      return;
    }
    this.nodeApps.splice(index, 1);

    // Clear node config when deleting the last app to
    // Ensure we are back in a non-modified state
    if (this.nodeApps.length === 0 && this.nodeConfigCreated) {
      this.node.nodeConfiguration = null;
      this.nodeConfigCreated = false;
    }

    // Notify about the change
    this.editNodeAppsEvent.emit();
    this.removeNodeAppEvent.emit(application);
  }

  onSelect(process: ApplicationConfiguration): void {
    this.selectAppConfigEvent.emit(process);
  }

  onPaste() {
    navigator.clipboard.readText().then(data => {
        let appConfig: ApplicationConfiguration = null;
        try {
          appConfig = JSON.parse(data) as ApplicationConfiguration;
        } catch (e) {
          this.mbService.open({title: 'Invalid Data', message: 'The data in the clipboard cannot be interpreted as application', mode: MessageBoxMode.WARNING});
          return;
        }

        // change OS if required
        if (!this.isClientApplicationsNode()) {
          appConfig.application = updateAppOs(appConfig.application, this.minionConfig.os);
        }

        const productKey = this.processConfig.instance.product;
        const appKey = appConfig.application;

        this.appService.getDescriptor(this.instanceGroupName, productKey, appKey).subscribe(desc => {
          if ((desc.type === ApplicationType.SERVER && this.isClientApplicationsNode()) || (desc.type === ApplicationType.CLIENT && !this.isClientApplicationsNode())) {
            this.mbService.open({title: 'Wrong Type', message: 'Application cannot be pasted on this node.', mode: MessageBoxMode.INFO});
            return;
          }

          // Generate unique identifier
          this.appService.createUuid(this.instanceGroupName).subscribe(uid => {

            appConfig.application.tag = productKey.tag;
            appConfig.uid = uid;

            // Update parameters for pasted app
            const apps = this.appService.getAllApps(this.processConfig);
            this.appService.updateApplicationParamsForPastedApplication(appConfig, desc, apps);

            // Insert at the end
            const targetIndex = this.nodeApps.length;
            this.addProcess(appConfig, targetIndex);
           });
        });
      },
      data => {
        this.log.error('Unable to paste from clipboard');
      });
  }

  fireEditAppConfigEvent(appConfig: ApplicationConfiguration) {
    this.editAppConfigEvent.emit(new EditAppConfigContext(this.node, appConfig, this.processConfig.instance.product));
  }

  fireEditEndpointsAppConfigEvent(appConfig: ApplicationConfiguration) {
    this.editAppEndpointsEvent.emit(new EditAppConfigContext(this.node, appConfig, this.processConfig.instance.product));
  }

  /** Returns whether or not at least one app has been added to the node */
  hasApps() {
    return this.nodeApps.length > 0;
  }

  /** Creates a new node configuration and initializes it with default value */
  createNewNodeConfig() {
    this.nodeConfigCreated = true;
    this.node.nodeConfiguration = cloneDeep(EMPTY_INSTANCE_NODE_CONFIGURATION);
    this.node.nodeConfiguration.uuid = this.processConfig.instance.uuid;
    this.node.nodeConfiguration.name = this.processConfig.instance.name;
    this.node.nodeConfiguration.autoStart = true;
    this.nodeApps = this.node.nodeConfiguration.applications;
  }

  isClientApplicationsNode(): boolean {
    return this.node.nodeName === CLIENT_NODE_NAME;
  }

  /**
   * Returns the drag&drop specific classes that are used to determine what can be dropped on this node.
   */
  getDragulaNodeClasses(): string[] {
    const classes: string[] = [];

    // when editing, set a minimum size here.
    if (this.manageApplications) {
      classes.push('init-zone');
    }

    // Name is used to identify source and target node during drop handling
    classes.push('dragula-nodeName-' + this.node.nodeName);

    // Append node type
    if (this.isClientApplicationsNode()) {
      classes.push('dragula-nodeType-client');
    } else {
      classes.push('dragula-nodeType-server');
    }

    // Append node OS - only server nodes have a state
    // NOTE: Config might contain entries where the node is not existing any more
    if (!this.isClientApplicationsNode()) {
      if (this.minionConfig) {
        classes.push('dragula-nodeOs-' + this.minionConfig.os.toLowerCase());
      } else {
        classes.push('dragula-nodeOs-offline');
      }
    }

    return classes;
  }

  /**
   * Returns the drag&drop specific classes that are used to determine where a specific app can be moved to.
   */
  getDragulaAppClasses(appConfig: ApplicationConfiguration): string[] {
    const classes: string[] = [];

    // Name is used to identify source and target node during drop handling
    classes.push('dragula-nodeName-' + this.node.nodeName);

    // Append app type
    if (this.isClientApplicationsNode()) {
      classes.push('dragula-appType-client');
    } else {
      classes.push('dragula-appType-server');
    }

    // Append OS of app
    const appOs = getAppOs(appConfig.application);
    classes.push('dragula-appOs-' + appOs.toLowerCase());
    return classes;
  }

  isNodeExisting() {
    return this.minionConfig != null;
  }

  isNodeOnline() {
    if (!this.isNodeExisting()) {
      return false;
    }
    return this.minionState && !this.minionState.offline;
  }

  getNodeStatusTooltip() {
    if (!this.isNodeExisting()) {
      return 'Node does not exist any more.';
    }
    if (!this.isNodeOnline()) {
      return this.minionState.infoText;
    }
    const startDate = format(new Date(this.minionState.startup), 'dd.MM.yyyy HH:mm:ss');
    const versionStr = convert2String(this.minionConfig.version);
    return 'Start time: ' + startDate + ' | ' + ' Version: ' + versionStr;
  }

  showNodePortList(template: TemplateRef<any>) {
    this.bottomSheet = this.bottomSheetSvc.open(template, { panelClass: 'process-sheet' });
    this.bottomSheet.afterDismissed().subscribe(_ => this.bottomSheet = null);
  }
}
