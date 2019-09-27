import { CdkDrag, CdkDragDrop, CdkDragExit, CdkDragStart, CdkDropList, CdkDropListContainer, moveItemInArray } from '@angular/cdk/drag-drop';
import { AfterViewInit, Component, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output, ViewChild } from '@angular/core';
import { cloneDeep } from 'lodash';
import { BehaviorSubject, Subscription } from 'rxjs';
import { ApplicationConfigurationCardComponent } from '../application-configuration-card/application-configuration-card.component';
import { ApplicationGroup } from '../models/application.model';
import { CLIENT_NODE_NAME, EMPTY_APPLICATION_CONFIGURATION, EMPTY_INSTANCE_NODE_CONFIGURATION, EMPTY_PROCESS_CONTROL_CONFIG } from '../models/consts';
import { EventWithCallback } from '../models/event';
import { ApplicationConfiguration, ApplicationDto, InstanceNodeConfiguration, InstanceNodeConfigurationDto } from '../models/gen.dtos';
import { EditAppConfigContext, ProcessConfigDto } from '../models/process.model';
import { ApplicationService } from '../services/application.service';
import { DownloadService } from '../services/download.service';
import { InstanceService } from '../services/instance.service';
import { Logger, LoggingService } from '../services/logging.service';
import { getAppOs } from '../utils/manifest.utils';
import { indexOf } from '../utils/object.utils';

/** Data attached to a drag event when dragging apps between nodes */
class InterDragData {
  constructor(
    public sourceNodeComponent: InstanceNodeCardComponent,
    public appCardComponent: ApplicationConfigurationCardComponent,
  ) {}
}

@Component({
  selector: 'app-instance-node-card',
  templateUrl: './instance-node-card.component.html',
  styleUrls: ['./instance-node-card.component.css'],
})
export class InstanceNodeCardComponent implements OnInit, OnDestroy, AfterViewInit {
  private readonly log: Logger = this.loggingService.getLogger('InstanceNodeCardComponent');

  /* CSS Classes attached to nodes to highligh valid / invalid drop zones */
  private readonly VALID_DROP_ZONE_CLASS = 'instance-node-valid-drop-zone';
  private readonly INVALID_DROP_ZONE_CLASS = 'instance-node-invalid-drop-zone';

  @Input() instanceGroupName: string;
  @Input() activatedInstanceTag: string;
  @Input() processConfig: ProcessConfigDto;
  @Input() productMissing: boolean;
  @Input() node: InstanceNodeConfigurationDto;
  @Input() dragStopped: BehaviorSubject<any>;
  @Input() dragStarted: BehaviorSubject<any>;
  @Input() manageApplications: boolean;
  @Input() isReadonly: boolean;

  @Output() editAppConfigEvent = new EventEmitter<EditAppConfigContext>();
  @Output() editNodeAppsEvent = new EventEmitter<void>();
  @Output() removeNodeAppEvent = new EventEmitter<ApplicationConfiguration>();
  @Output() selectAppConfigEvent = new EventEmitter<ApplicationConfiguration>();
  @Output() downloadClickAndStartEvent = new EventEmitter<ApplicationConfiguration>();
  @Output() downloadInstallerEvent = new EventEmitter<EventWithCallback<ApplicationConfiguration>>();

  @ViewChild(CdkDropList) placeholder: CdkDropList;
  @ViewChild('appDropZone') appDropZone: ElementRef;
  @ViewChild('appDropPlaceholder') appDropPlaceholder: ElementRef;
  @ViewChild('appNodeCard', { read: ElementRef }) appNodeCard: ElementRef;

  // Flag that we dynamically created it's config
  private nodeConfigCreated = false;

  public target: CdkDropList;
  public targetIndex: number;
  public source: CdkDropListContainer;
  public sourceIndex: number;

  private startSubscription: Subscription;
  private stopSubscription: Subscription;

  constructor(
    private loggingService: LoggingService,
    private appService: ApplicationService,
    private instanceService: InstanceService,
    private downloadService: DownloadService,
  ) {}

  ngOnInit() {
    this.startSubscription = this.dragStarted.subscribe(x => this.startDrag(x));
    this.stopSubscription = this.dragStopped.subscribe(x => this.stopDrag(x));
  }

  ngOnDestroy(): void {
    this.stopSubscription.unsubscribe();
    this.startSubscription.unsubscribe();
  }

  ngAfterViewInit() {
    this.updateDropZonePlaceholder();
  }

  getInterDragData(applicationConfigurationCardComponent: ApplicationConfigurationCardComponent) {
    return new InterDragData(this, applicationConfigurationCardComponent);
  }

  isDragDisabled(applicationConfiguration: ApplicationConfiguration): boolean {
    return !this.manageApplications || this.appService.isMissing(applicationConfiguration.application);
  }

  /**
   * Removes highlighting whether or not dropping is allowed
   */
  stopDrag(event: any) {
    // Remove classes which zones are valid
    const cardClassList = this.appNodeCard.nativeElement.classList;
    cardClassList.remove(this.VALID_DROP_ZONE_CLASS);
    cardClassList.remove(this.INVALID_DROP_ZONE_CLASS);

    // Cleanup internal data what was dragged
    this.sourceIndex = -1;
    this.targetIndex = -1;
    this.source = null;
    this.target = null;
  }

  /**
   * Highlights the node when dropping is allowed or disallowed
   */
  startDrag(event: CdkDragStart<any>) {
    if (!event) {
      return;
    }
    const cardClassList = this.appNodeCard.nativeElement.classList;
    if (event.source.data instanceof ApplicationGroup) {
      if (this.isSupported(event.source.data)) {
        cardClassList.add(this.VALID_DROP_ZONE_CLASS);
      } else {
        cardClassList.add(this.INVALID_DROP_ZONE_CLASS);
      }
    }
    if (event.source.data instanceof InterDragData) {
      const appCard = event.source.data.appCardComponent;
      if (appCard.isClient) {
        if (this.isClientApplicationsNode()) {
          cardClassList.add(this.VALID_DROP_ZONE_CLASS);
        } else {
          cardClassList.add(this.INVALID_DROP_ZONE_CLASS);
        }
      } else {
        // Offline nodes do not have a status
        const nodeStatus = this.node.status;
        if (!nodeStatus) {
          cardClassList.add(this.INVALID_DROP_ZONE_CLASS);
        } else {
          const appOs = getAppOs(appCard.appConfig.application);
          if (nodeStatus.os === appOs) {
            cardClassList.add(this.VALID_DROP_ZONE_CLASS);
          } else {
            cardClassList.add(this.INVALID_DROP_ZONE_CLASS);
          }
        }
      }
    }
  }

  /**
   * Called when an element is moved away from this node
   */
  dropListExited(event: CdkDragExit<any>) {
    this.updateDropZonePlaceholder();
  }

  /**
   * Called when the user drags the application from one node to another node.
   */
  removeProcess(application: ApplicationConfiguration) {
    const apps = this.node.nodeConfiguration.applications;
    const index = apps.indexOf(application);
    if (index === -1) {
      return;
    }
    apps.splice(index, 1);
    this.updateDropZonePlaceholder();

    // Clear node config when deleting the last app to
    // Ensure we are back in a non-modified state
    if (apps.length === 0 && this.nodeConfigCreated) {
      this.node.nodeConfiguration = null;
      this.nodeConfigCreated = false;
    }

    // Notify about the removal
    this.editNodeAppsEvent.emit();
    this.removeNodeAppEvent.emit(application);
  }

  /**
   * Called when the user drops an existing application on this node or
   * when the user drops a new application group from the sidebar
   */
  dropListDropped(event: CdkDragDrop<any>) {
    if (!this.target) {
      this.dragStopped.next(this.node.nodeName);
      return;
    }
    const data = event.item.data;
    if (data instanceof ApplicationGroup) {
      this.dropGroup(event);
    } else {
      this.dropApp(event);
    }

    // Notify that drag is done
    this.editNodeAppsEvent.emit();
    this.dragStopped.next(this.node.nodeName);

    // Cleanup placeholder
    this.updateDropZonePlaceholder();
  }

  /**
   * Called when the user drops a new application group from the sidebar into this node.
   */
  dropGroup(event: CdkDragDrop<ApplicationGroup>) {
    // Check if dropping is supported here
    const group = event.item.data;
    if (!this.isSupported(group)) {
      return;
    }

    // Initialize node configuration
    if (!this.node.nodeConfiguration) {
      this.createNewNodeConfig();
    }

    // Create configurations for all supported OS
    if (this.isClientApplicationsNode()) {
      for (const app of group.applications) {
        const newCfg = this.createNewAppConfig(app);
        this.node.nodeConfiguration.applications.splice(this.targetIndex, 0, newCfg);
      }
    } else {
      const nodeOs = this.node.status.os;
      const app = group.getAppFor(nodeOs);
      const newCfg = this.createNewAppConfig(app);
      this.node.nodeConfiguration.applications.splice(this.targetIndex, 0, newCfg);
    }
  }

  /**
   * Called when the user drops an existing application from another node into this node.
   */
  dropApp(event: CdkDragDrop<any>) {
    const phElement = this.placeholder.element.nativeElement;
    const parent = phElement.parentNode;

    const data: InterDragData = event.item.data;
    const sourceNode = data.sourceNodeComponent;
    const droppedApp = data.appCardComponent;

    // Drop to another node: Move item data wise.
    if (sourceNode !== this) {
      const node = this.node;
      if (!node.nodeConfiguration) {
        this.createNewNodeConfig();
      }
      const apps = this.node.nodeConfiguration.applications;
      apps.splice(this.targetIndex, 0, droppedApp.appConfig);
      sourceNode.removeProcess(droppedApp.appConfig);
    }

    // Drop within the same node. Re-Sort according to source.
    if (this.sourceIndex !== -1) {
      parent.insertBefore(this.source.element.nativeElement, parent.children[this.sourceIndex]);
      if (this.sourceIndex !== this.targetIndex) {
        moveItemInArray(this.node.nodeConfiguration.applications, this.sourceIndex, this.targetIndex);
      }
    }
  }

  updateDropZonePlaceholder() {
    if (!this.placeholder) {
      return;
    }
    const phElement = this.placeholder.element.nativeElement;

    const nodeConfig = this.node.nodeConfiguration;
    if (nodeConfig && nodeConfig.applications.length > 0) {
      phElement.style.display = 'none';
      if (phElement.parentNode) {
        phElement.parentNode.removeChild(phElement);
      }
      this.hideDropZonePlaceholder();
    } else {
      phElement.style.display = '';
      if (!phElement.parentNode) {
        this.appDropZone.nativeElement.appendChild(phElement);
      }
      this.showDropZonePlaceholder();
    }
  }

  hideDropZonePlaceholder() {
    if (!this.appDropPlaceholder) {
      return;
    }
    const zoneElement = this.appDropPlaceholder.nativeElement;
    zoneElement.style.display = 'none';
  }

  showDropZonePlaceholder() {
    if (!this.appDropPlaceholder) {
      return;
    }
    const zoneElement = this.appDropPlaceholder.nativeElement;
    zoneElement.style.display = '';
  }

  /** Predicate called when entering the drop zone */
  enterDropList = (drag: CdkDrag, drop: CdkDropList) => {
    // check type of new application
    if (drag.data instanceof ApplicationGroup) {
      if (!this.isSupported(drag.data)) {
        return false;
      }
    }

    // check type when dragging between nodes
    if (drag.data instanceof InterDragData) {
      // Client app may only be dragged to a client node
      const appCard = drag.data.appCardComponent;
      if (this.isClientApplicationsNode()) {
        return appCard.isClient;
      }

      // Server app must have the same OS as the node
      // Node that is offline does not have a status
      if (!this.node.status) {
        return false;
      }
      const nodeOs = this.node.status.os;
      const appOs = getAppOs(appCard.appConfig.application);
      if (nodeOs !== appOs) {
        return false;
      }
    }

    // whether the drop will be allowed or not. it is ONLY allowed on the placeholder list.
    let allowed = false;

    // we're entering the placeholder drop-zone. Happens when dragging from external and
    // when switching drop zones (see below).
    if (drop === this.placeholder) {
      allowed = true;
      this.hideDropZonePlaceholder();

      // only initially, we need to make sure things are setup when dragging from externally,
      // once source and target are set up, this is ok. It happens when the drop target
      // is swapped below so that it is the placeholder instead of the actual element
      if (this.target && this.source) {
        return true;
      }
    }

    const phElement = this.placeholder.element.nativeElement;
    const dropElement = drop.element.nativeElement;

    // source index when dragging within the same component, otherwise -1
    const dragIndex = indexOf(dropElement.parentNode.children, drag.dropContainer.element.nativeElement);
    const dropIndex = indexOf(dropElement.parentNode.children, dropElement);

    if (!this.source) {
      this.sourceIndex = dragIndex;
      this.source = drag.dropContainer;

      const sourceElement = this.source.element.nativeElement;
      if (drag.data instanceof InterDragData) {
        const dragData = drag.data;
        if (dragData.sourceNodeComponent === this && sourceElement.parentElement) {
          sourceElement.parentNode.removeChild(sourceElement);
        }
      }
    }

    this.targetIndex = dropIndex;
    this.target = drop;

    phElement.style.display = '';
    if (dragIndex !== -1) {
      // drag within the same container
      dropElement.parentNode.insertBefore(phElement, dragIndex < dropIndex ? dropElement.nextSibling : dropElement);
    } else {
      dropElement.parentNode.insertBefore(phElement, dropElement);
    }

    this.source.start();
    this.placeholder.enter(drag, drag.element.nativeElement.offsetLeft, drag.element.nativeElement.offsetTop);

    return allowed;
  }

  onSelect(process: ApplicationConfiguration): void {
    this.selectAppConfigEvent.emit(process);
  }

  fireEditAppConfigEvent(appConfig: ApplicationConfiguration) {
    this.editAppConfigEvent.emit(new EditAppConfigContext(this.node, appConfig));
  }

  /** Creates a new node configuration and initializes it with default value */
  createNewNodeConfig() {
    this.nodeConfigCreated = true;
    this.node.nodeConfiguration = cloneDeep(EMPTY_INSTANCE_NODE_CONFIGURATION);
    this.node.nodeConfiguration.uuid = this.processConfig.instance.uuid;
    this.node.nodeConfiguration.name = this.processConfig.instance.name;
    this.node.nodeConfiguration.autoStart = true;
  }

  /** Creates a new application configuration and initializes it with default values */
  createNewAppConfig(app: ApplicationDto): ApplicationConfiguration {
    const appConfig = cloneDeep(EMPTY_APPLICATION_CONFIGURATION);
    appConfig.processControl = cloneDeep(EMPTY_PROCESS_CONTROL_CONFIG);
    appConfig.application = app.key;
    appConfig.name = app.name;

    // default process control configuration
    const processControlDesc = app.descriptor.processControl;
    const processControlConfig = appConfig.processControl;
    processControlConfig.gracePeriod = processControlDesc.gracePeriod;
    if (processControlDesc.supportedStartTypes) {
      processControlConfig.startType = processControlDesc.supportedStartTypes[0];
    }
    processControlConfig.keepAlive = processControlDesc.supportsKeepAlive;
    processControlConfig.noOfRetries = processControlDesc.noOfRetries;

    // Lookup parameter in all available applications
    const apps = this.appService.getAllApps(this.processConfig);

    // Load descriptor and initialize configuration
    const productKey = this.processConfig.instance.product;
    const appKey = appConfig.application;
    this.appService.getDescriptor(this.instanceGroupName, productKey, appKey).subscribe(desc => {
      // Generate unique identifier
      this.appService.createUuid(this.instanceGroupName).subscribe(uid => {
        appConfig.uid = uid;
        this.appService.initAppConfig(appConfig, desc, apps);
        this.editNodeAppsEvent.emit();
      });
    });
    return appConfig;
  }

  needEmptyHint(): boolean {
    if (this.manageApplications) {
      return false;
    }

    if (!this.node || !this.node.nodeConfiguration || !this.node.nodeConfiguration.applications) {
      return true;
    }

    return this.node.nodeConfiguration.applications.length === 0;
  }

  countForeignApps(foreign: InstanceNodeConfiguration[]): number {
    let count = 0;

    if (!foreign || foreign.length === 0) {
      return count;
    }

    for (const x of foreign) {
      count += x.applications.length;
    }

    return count;
  }

  getForeignInstanceText(): string {
    const count = this.countForeignApps(this.node.foreignNodeConfigurations);
    const appPlural = count === 1 ? '' : 's';
    const instanceCount = this.node.foreignNodeConfigurations.length;
    const instancePlural = instanceCount === 1 ? '' : 's';
    const isAre = count === 1 ? 'is' : 'are';

    return `${count} application${appPlural} from ${instanceCount} instance${instancePlural} ${isAre} configured for this node.`;
  }

  isClientApplicationsNode(): boolean {
    return this.node && this.node.nodeName === CLIENT_NODE_NAME;
  }

  /** Returns whether or not the given application is supported by this node */
  isSupported(appGroup: ApplicationGroup) {
    if (this.isClientApplicationsNode() && appGroup.isClientApp()) {
      return true;
    }

    // Server nodes might be offline
    if (this.node.status && appGroup.isServerApp()) {
      const os = this.node.status.os;
      if (appGroup.operatingSystems.includes(os)) {
        return true;
      }
    }
    return false;
  }
}
