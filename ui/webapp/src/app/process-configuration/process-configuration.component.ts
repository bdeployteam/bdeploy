import { CdkDrag, CdkDragDrop, CdkDragStart, CdkDropList } from '@angular/cdk/drag-drop';
import { Location } from '@angular/common';
import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { MatDialog, MatDialogConfig, MatSlideToggle } from '@angular/material';
import { ActivatedRoute, Params } from '@angular/router';
import { cloneDeep, isEqual } from 'lodash';
import { BehaviorSubject, forkJoin, Observable, of, Subscription } from 'rxjs';
import { finalize, mergeMap } from 'rxjs/operators';
import { ApplicationEditComponent } from '../application-edit/application-edit.component';
import { FileUploadComponent } from '../file-upload/file-upload.component';
import { InstanceVersionCardComponent } from '../instance-version-card/instance-version-card.component';
import { MessageBoxMode } from '../messagebox/messagebox.component';
import { ApplicationGroup } from '../models/application.model';
import { CLIENT_NODE_NAME, EMPTY_DEPLOYMENT_STATE } from '../models/consts';
import { EventWithCallback } from '../models/event';
import { ApplicationConfiguration, ApplicationDto, DeploymentStateDto, InstanceNodeConfiguration, InstanceNodeConfigurationDto, ManifestKey, ProductDto } from '../models/gen.dtos';
import { EditAppConfigContext, ProcessConfigDto } from '../models/process.model';
import { ProcessDetailsComponent } from '../process-details/process-details.component';
import { ApplicationService } from '../services/application.service';
import { DownloadService } from '../services/download.service';
import { InstanceService } from '../services/instance.service';
import { LauncherService } from '../services/launcher.service';
import { Logger, LoggingService } from '../services/logging.service';
import { MessageboxService } from '../services/messagebox.service';
import { ProcessService } from '../services/process.service';
import { ProductService } from '../services/product.service';
import { sortByTags } from '../utils/manifest.utils';

export enum SidenavMode {
  Applications,
  Versions,
  Products,
  ProcessStatus,
  ClientInfo,
}

@Component({
  selector: 'app-process-configuration',
  templateUrl: './process-configuration.component.html',
  styleUrls: ['./process-configuration.component.css'],
  providers: [ApplicationService, LauncherService],
})
export class ProcessConfigurationComponent implements OnInit, OnDestroy {
  public static readonly DROPLIST_APPLICATIONS = 'APPLICATIONS';
  private readonly log: Logger = this.loggingService.getLogger('ProcessConfigurationComponent');

  @ViewChild(ApplicationEditComponent)
  private editComponent: ApplicationEditComponent;

  @ViewChild(ProcessDetailsComponent)
  private processDetails: ProcessDetailsComponent;

  public groupParam: string;
  public uuidParam: string;

  public selectedConfig: ProcessConfigDto;
  public processConfigs: ProcessConfigDto[] = [];
  public selectedProcess: ApplicationConfiguration;

  public sidenavMode: SidenavMode = SidenavMode.Versions;
  private subscription: Subscription;

  public deploymentState: DeploymentStateDto = EMPTY_DEPLOYMENT_STATE;
  public productTags: ProductDto[];

  public dragStop = new BehaviorSubject<any>(null);
  public dragStart = new BehaviorSubject<any>(null);

  public editMode = false;
  public editAppConfigContext: EditAppConfigContext;
  public activeNodeConfig: InstanceNodeConfiguration;

  public cancelEnabled = true;
  public saveEnabled = false;
  public discardEnabled = false;
  public applyEnabled = false;

  public loading = true;
  public productsLoading = false;
  public isRunningOutOfSync = false;

  // Refresh timer and configuration
  public readonly AUTO_REFRESH_INTERVAL_SEC = 10;
  public lastAutoRefresh = Date.now();
  public autoRefresh = false;
  public autoRefreshHandle: any;
  public nextAutoRefreshSec: number;
  public processSubscription: Subscription;

  constructor(
    private route: ActivatedRoute,
    private loggingService: LoggingService,
    private instanceService: InstanceService,
    private applicationService: ApplicationService,
    private clientApps: LauncherService,
    private messageBoxService: MessageboxService,
    private productService: ProductService,
    private processService: ProcessService,
    public location: Location,
    public downloadService: DownloadService,
    private dialog: MatDialog
  ) {}

  ngOnInit() {
    this.subscription = this.route.params.subscribe((p: Params) => {
      this.groupParam = p['group'];
      this.uuidParam = p['uuid'];
      this.loadVersions();
      this.enableAutoRefresh();
      this.doTriggerProcessStatusUpdate();
    });
    this.processSubscription = this.processService.subscribe(() => this.onProcessStatusChanged());
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    if (this.autoRefreshHandle) {
      clearInterval(this.autoRefreshHandle);
    }
    this.processSubscription.unsubscribe();
  }

  private loadVersions(): void {
    this.instanceService.listInstanceVersions(this.groupParam, this.uuidParam).subscribe(versions => {
      this.log.debug('got ' + versions.length + ' instance versions');
      versions.sort((a, b) => {
        return +b.key.tag - +a.key.tag;
      });

      // Create new config DTO for all versions
      this.processConfigs.splice(0, this.processConfigs.length);
      versions.forEach(v => {
        this.processConfigs.push(new ProcessConfigDto(v, true));
      });

      // Add specialized entry that is shown in case we have local modifications
      this.processConfigs.unshift(new ProcessConfigDto(this.processConfigs[0].version, false));

      // get deployment states
      this.instanceService.getDeploymentStates(this.groupParam, this.uuidParam).subscribe(deploymentState => {
        this.deploymentState = deploymentState;
        // try to load the activated version
        if (this.deploymentState.activatedVersion) {
          const initialConfig = this.processConfigs.find(cfg => cfg.version.key.tag === this.deploymentState.activatedVersion);
          if (initialConfig) {
            this.loadInstance(initialConfig);
            return;
          }
        }
        // start with first version otherwise
        this.loadInstance(this.processConfigs[0]);
      });
    });
  }

  private loadInstance(newSelectedConfig: ProcessConfigDto) {
    // Check if we already loaded this version
    if (newSelectedConfig.instance) {
      this.selectedConfig = newSelectedConfig;
      this.updateDirtyStateAndValidate();
      return;
    }
    this.loading = true;

    const selectedVersion = newSelectedConfig.version;

    const call1 = this.instanceService
      .getInstanceVersion(this.groupParam, this.uuidParam, selectedVersion.key.tag)
      .pipe(
        mergeMap(instance => {
          newSelectedConfig.setInstance(instance);
          return this.applicationService.listApplications(this.groupParam, newSelectedConfig.instance.product); // => results[0]
        }),
      );
    const call2 = this.instanceService.getNodeConfiguration(this.groupParam, this.uuidParam, selectedVersion.key.tag); // => results[1]
    const call3 = this.productService.getProducts(this.groupParam); // => result[3];

    forkJoin([call1, call2, call3]).subscribe(results => {
      newSelectedConfig.setNodeList(results[1]);
      newSelectedConfig.setApplications(results[0]);
      this.selectedConfig = newSelectedConfig;

      const filtered = results[2].filter(x => x.key.name === this.selectedConfig.version.product.name);
      this.productTags = sortByTags(filtered, p => p.key.tag, false);

      this.loading = false;
      this.updateDirtyStateAndValidate();
      this.onProcessStatusChanged();
      this.createStickyHeader();
    });
  }

  loadDeploymentStates(finalizer: () => void) {
    this.instanceService
      .getDeploymentStates(this.groupParam, this.uuidParam)
      .pipe(finalize(finalizer))
      .subscribe(r => {
        this.deploymentState = r;
        this.doTriggerProcessStatusUpdate();
      });
  }

  createStickyHeader() {
    const document = window.document;
    const header = document.getElementById('page-header');
    const content = document.getElementById('app-content');
    content.onscroll = function() {
      const sticky = header.offsetTop;
      if (content.scrollTop > sticky) {
        header.classList.add('sticky-header');
        header.classList.add('mat-elevation-z1');
      } else {
        header.classList.remove('sticky-header');
        header.classList.remove('mat-elevation-z1');
      }
    };
  }

  public getDropListData(): any {
    return ProcessConfigurationComponent.DROPLIST_APPLICATIONS;
  }

  canDeactivate(): Observable<boolean> {
    if (!this.isDirty()) {
      return of(true);
    }
    return this.messageBoxService.open({
      title: 'Unsaved changes',
      message: 'Instance was modified. Close without saving?',
      mode: MessageBoxMode.CONFIRM_WARNING,
    });
  }

  isSidenavApplications(): boolean {
    return this.sidenavMode === SidenavMode.Applications;
  }

  isSidenavVersions(): boolean {
    return this.sidenavMode === SidenavMode.Versions;
  }

  isSidenavProducts(): boolean {
    return this.sidenavMode === SidenavMode.Products;
  }

  isSidenavProcessStatus(): boolean {
    return this.sidenavMode === SidenavMode.ProcessStatus;
  }

  isSidenavClientInfo() {
    return this.sidenavMode === SidenavMode.ClientInfo;
  }

  setSidenavVersions(): void {
    this.sidenavMode = SidenavMode.Versions;
    this.enableAutoRefresh();
  }

  setSidenavApplications(): void {
    this.sidenavMode = SidenavMode.Applications;
    this.disableAutoRefresh();
  }

  setSidenavProcessStatus(process: ApplicationConfiguration): void {
    const callRefresh = this.selectedProcess === process;
    this.sidenavMode = SidenavMode.ProcessStatus;
    this.selectedProcess = process;
    if (callRefresh && this.processDetails) {
      this.processDetails.reLoadStatus();
    }
  }

  setSideNavClientInfo(process: ApplicationConfiguration) {
    this.sidenavMode = SidenavMode.ClientInfo;
    this.selectedProcess = process;
  }

  setSidenavProducts(): void {
    this.sidenavMode = SidenavMode.Products;
    this.disableAutoRefresh();
  }

  public hasClientApplications(): boolean {
    return this.selectedConfig.hasClientApplications();
  }

  public hasServerApplications(): boolean {
    return this.selectedConfig.hasServerApplications();
  }

  onDownloadClickAndStart(app: ApplicationConfiguration) {
    this.instanceService.createClickAndStartDescriptor(this.groupParam, this.uuidParam, app.uid).subscribe(data => {
      this.downloadService.downloadData(app.name + '.bdeploy', data);
    });
  }

  onDownloadInstaller(event: EventWithCallback<ApplicationConfiguration>) {
    const app = event.data;
    this.instanceService
      .createClientInstaller(this.groupParam, this.uuidParam, app.uid)
      .pipe(finalize(() => event.done()))
      .subscribe(token => {
        window.location.href = this.instanceService.downloadClientInstaller(this.groupParam, this.uuidParam, app.uid, token);
      });
  }

  /** Predicate called when entering the drop zone */
  enterDropList = (drag: CdkDrag, drop: CdkDropList) => {
    return false; // Do not allow dragging apps from nodes to the sidebar
  }

  public isProductAvailable(config: ProcessConfigDto): boolean {
    return this.productTags.find(p => isEqual(p.key, config.version.product)) !== undefined;
  }

  public isProductUpgradeAvailable(): boolean {
    if (this.processConfigs && this.processConfigs.length > 0 && this.productTags && this.productTags.length > 0) {
      return this.processConfigs[0].version.product.tag !== this.productTags[0].key.tag;
    }
    return false;
  }

  /**
   * Called to switch the active configuration. Needs some special treatment as we have
   * a virtual configuration in our list that is only shown if we have local changes.
   */
  onSelectConfig(config: ProcessConfigDto): void {
    if (this.loading || !this.isProductAvailable(config)) {
      return;
    }

    // This is the node shown if we have local changes
    const virtualConfig = this.processConfigs[0];

    // If we show the virtual node we can simply switch to whatever node the user selected.
    if (virtualConfig.dirty) {
      this.loadInstance(config);
      return;
    }

    // We do not show the node indicating that we have local changes.
    // If the user clicks on the latest node we need to internally switch to
    // the virtual node. Only this one can be modified
    const latestConfig = this.processConfigs[1];
    if (latestConfig === config) {
      this.loadInstance(virtualConfig);
      return;
    }

    // User clicked at any other node -> just load it
    this.loadInstance(config);
  }

  /**
   * Called when the user drops an application in the sidebar.
   */
  public drop(event: CdkDragDrop<any>): void {
    this.dragStop.next(event);
  }

  /**
   * Called when the user starts dragging an application.
   */
  public dragStarted(event: CdkDragStart<ApplicationGroup>): void {
    this.dragStart.next(event);
  }

  /**
   * Called when the user clicks the SAVE button in the process configuration
   */
  onSave(): void {
    const nodePromise = this.instanceService.updateInstance(
      this.groupParam,
      this.uuidParam,
      this.selectedConfig.instance,
      this.selectedConfig.nodeList,
      this.processConfigs[0].version.key.tag,
    );
    nodePromise.subscribe(x => {
      this.loadVersions();
    });
  }

  /**
   * Called when the user clicks the DISCARD button in the process configuration
   */
  async onDiscardChanges(): Promise<void> {
    const result = await this.messageBoxService.openAsync({
      title: 'Discard changes',
      message: 'Are you sure you want to discard all local changes?',
      mode: MessageBoxMode.QUESTION,
    });

    // Revert changes done in the virtual node
    if (!result) {
      return;
    }
    const virtualConfig = this.processConfigs[0];
    virtualConfig.discardChanges();

    // Update enabled state of buttons
    this.updateDirtyStateAndValidate();
  }

  public onSelectApp(node: InstanceNodeConfigurationDto, process: ApplicationConfiguration) {
    // Prevent switching if we edit applications or products
    const disallowed = [SidenavMode.Applications, SidenavMode.Products];
    if (disallowed.includes(this.sidenavMode)) {
      return;
    }
    if (node.nodeName === CLIENT_NODE_NAME) {
      this.setSideNavClientInfo(process);
    } else {
      this.setSidenavProcessStatus(process);
    }
  }

  public onEditApp(nodeConfig: InstanceNodeConfiguration, context: EditAppConfigContext) {
    this.activeNodeConfig = nodeConfig;
    this.editAppConfigContext = cloneDeep(context);
    this.setEditMode(true);
  }

  public onApplyAppChanges() {
    const updated = this.editComponent.appConfigContext.applicationConfiguration;
    const appDesc = this.editComponent.appDesc;
    const appIdx = this.activeNodeConfig.applications.findIndex(app => app.uid === updated.uid);
    this.activeNodeConfig.applications[appIdx] = updated;

    // Update global parameters of all apps
    this.applicationService.updateGlobalParameters(appDesc, updated, this.selectedConfig);

    // Exit edit mode and validate all
    this.setEditMode(false);
  }

  public onDiscardAppChanges(askUser: boolean) {
    if (askUser) {
      const resultPromise = this.editComponent.canDeactivate();
      resultPromise.subscribe(result => {
        if (result) {
          this.setEditMode(false);
        }
      });
    } else {
      this.setEditMode(false);
    }
  }

  isVersionSelected(config: ProcessConfigDto) {
    const versionMatch = isEqual(config.version.key, this.selectedConfig.version.key);

    // If we have local changes we show a specialized card
    // It has the same version but an immutable flag
    const hasModifications = this.processConfigs[0].dirty;
    if (versionMatch && hasModifications) {
      return config.readonly === this.selectedConfig.readonly;
    }

    // When we do not have local changes the version must match
    return versionMatch;
  }

  public shouldShowNode(node: InstanceNodeConfigurationDto) {
    const isClientNode = node.nodeName === CLIENT_NODE_NAME;
    const productHasClientApps = this.hasClientApplications();
    const hasConfiguredClientApp =
      node.nodeConfiguration && node.nodeConfiguration.applications && node.nodeConfiguration.applications.length > 0;
    return !isClientNode || (isClientNode && productHasClientApps) || hasConfiguredClientApp;
  }

  shouldShowVersion(toggle: MatSlideToggle, config: ProcessConfigDto): boolean {
    const virtualConfig = this.processConfigs[0];

    // Show virtual config only if we have local changes
    if (virtualConfig === config) {
      return virtualConfig.dirty;
    }

    // Show all versions
    if (toggle.checked) {
      return true;
    }

    // fail while not initialized
    if (!this.selectedConfig) {
      return false;
    }

    const version = config.version;
    const newestVersion = this.processConfigs[1].version;
    const selectedVersion = this.selectedConfig.version;

    // newest version
    const v: number = +version.key.tag;
    if (v >= +newestVersion.key.tag) {
      return true;
    }
    // activated version
    if (this.deploymentState && this.deploymentState.activatedVersion && v >= +this.deploymentState.activatedVersion) {
      return true;
    }
    // selected version
    if (selectedVersion && v >= +selectedVersion.key.tag) {
      return true;
    }
    // App is running in this version
    return this.isRunningOrScheduledVersion(version.key.tag);
  }

  public isRunningOrScheduledVersion(instanceTag: string): boolean {
    return this.processService.isRunningOrScheduledInVersion(instanceTag);
  }

  public isValid() {
    if (this.editMode && this.editComponent) {
      return this.editComponent.isValid();
    }
    return this.selectedConfig.valid;
  }

  public isDirty() {
    if (this.editMode && this.editComponent) {
      return this.editComponent.isDirty();
    }
    // We always check the dirty flag of the virtual configuration
    // to alert the user when there are unsaved changes and he wants to leaves the page
    const virtualConfig = this.processConfigs[0];
    return virtualConfig.dirty;
  }

  /**
   * Called whenever an application on a node is added, removed or updated.
   */
  onUpdateNodeApps() {
    this.updateDirtyStateAndValidate();
  }

  /**
   * Validates all applications of all nodes and calculates the dirty state.
   */
  updateDirtyStateAndValidate() {
    // Validation and dirty handling is only done if the configuration can be modified
    if (!this.selectedConfig.readonly) {
      // Validate all apps of all nodes
      this.applicationService.validate(this.selectedConfig);

      // Recalculate dirty state of applications
      this.applicationService.calculateDirtyState(this.selectedConfig);

      // Track dirty and validation issues
      this.selectedConfig.dirty = this.applicationService.isOneDirty();
      this.selectedConfig.valid = this.applicationService.isAllValid();
    } else {
      this.applicationService.clearState();
    }

    // Cancel is always enabled if we are finished with loading
    this.cancelEnabled = !this.loading;

    // Save is only enabled if we have local changes that are valid
    if (this.editMode && this.editComponent) {
      this.saveEnabled = this.editComponent.isDirty() && this.editComponent.isValid();
      this.discardEnabled = false;
    } else {
      // Bind save button state to the virtual configuration
      // Thus save is enabled if there are changes regardless of the selection
      // Use this.selectedConfig.dirty to bind enabled state to selection
      const virtualConfig = this.processConfigs[0];
      this.discardEnabled = virtualConfig.dirty;
      this.saveEnabled = virtualConfig.dirty && virtualConfig.valid;
    }
  }

  doInstallVersion(manifest: ManifestKey, card: InstanceVersionCardComponent) {
    card.isLoading = true;
    const resultPromise = this.instanceService.install(this.groupParam, this.uuidParam, manifest);
    resultPromise
      .pipe(
        finalize(() => {
          this.loadDeploymentStates(() => (card.isLoading = false));
        }),
      )
      .subscribe(_ => {});
  }

  doUninstallVersion(manifest: ManifestKey, card: InstanceVersionCardComponent) {
    card.isLoading = true;
    const resultPromise = this.instanceService.uninstall(this.groupParam, this.uuidParam, manifest);
    resultPromise
      .pipe(
        finalize(() => {
          this.loadDeploymentStates(() => (card.isLoading = false));
        }),
      )
      .subscribe(_ => {});
  }

  doActivateVersion(manifest: ManifestKey, card: InstanceVersionCardComponent) {
    card.isLoading = true;

    const resultPromise = this.instanceService.activate(this.groupParam, this.uuidParam, manifest);
    resultPromise
      .pipe(
        finalize(() => {
          this.loadDeploymentStates(() => (card.isLoading = false));
          const newSelectedConfig = this.processConfigs.find(cfg => cfg.version.key.tag === manifest.tag);
          if (newSelectedConfig) {
            this.loadInstance(newSelectedConfig);
          }
        }),
      )
      .subscribe(_ => {});
  }

  shouldShowProduct(toggle: MatSlideToggle, tag: ProductDto): boolean {
    if (toggle.checked) {
      return true;
    }

    return tag.key.tag >= this.selectedConfig.version.product.tag;
  }

  /**
   * Returns whether or not the currently selected configuration is editable.
   */
  isEditable() {
    return this.selectedConfig && !this.selectedConfig.readonly;
  }

  updateProduct(product: ProductDto): void {
    this.productsLoading = true;
    this.productService.updateProduct(this.selectedConfig, product);

    // Fetch applications of the new product and update the config
    this.applicationService
      .listApplications(this.groupParam, product.key)
      .pipe(finalize(() => (this.productsLoading = false)))
      .subscribe(apps => {
        this.updateApplications(apps);
      });
  }

  updateApplications(apps: ApplicationDto[]) {
    this.applicationService.updateApplications(this.selectedConfig, apps);
    this.updateDirtyStateAndValidate();
    this.setSidenavVersions();
  }

  startInstance() {
    this.processService.startAll(this.groupParam, this.uuidParam).subscribe(r => {
      this.doTriggerProcessStatusUpdate();
      if (this.processDetails) {
        this.processDetails.reLoadStatus();
      }
    });
  }

  stopInstance() {
    this.processService.stopAll(this.groupParam, this.uuidParam).subscribe(r => {
      this.doTriggerProcessStatusUpdate();
      if (this.processDetails) {
        this.processDetails.reLoadStatus();
      }
    });
  }

  restartInstance() {
    this.processService.restartAll(this.groupParam, this.uuidParam).subscribe(r => {
      this.doTriggerProcessStatusUpdate();
      if (this.processDetails) {
        this.processDetails.reLoadStatus();
      }
    });
  }

  /** Switches the edit to the desired state */
  setEditMode(editMode: boolean) {
    this.editMode = editMode;
    if (editMode) {
      this.disableAutoRefresh();
    } else {
      this.enableAutoRefresh();
      this.updateDirtyStateAndValidate();
    }
  }

  /** Toggles the state of the auto-refresh */
  toggleAutoRefresh() {
    if (this.autoRefresh) {
      this.disableAutoRefresh();
    } else {
      this.enableAutoRefresh();
    }
  }

  /** Enables auto-refresh mechanism */
  enableAutoRefresh() {
    if (this.autoRefresh) {
      return;
    }

    // Enable timer to execute regular updates
    this.autoRefresh = true;
    this.autoRefreshHandle = setInterval(() => this.doUpdateAutoRefreshProgress(), 1000);

    // Execute refresh immediately
    this.doTriggerProcessStatusUpdate();
    this.doUpdateAutoRefreshProgress();
  }

  /** Disables the auto-refresh mechanism */
  disableAutoRefresh() {
    if (!this.autoRefresh) {
      return;
    }
    this.autoRefresh = false;
    clearInterval(this.autoRefreshHandle);
  }

  /** Called when the process state has been refreshed */
  onProcessStatusChanged() {
    const activatedTag = this.deploymentState.activatedVersion;
    if (!activatedTag) {
      this.isRunningOutOfSync = false;
    } else {
      this.isRunningOutOfSync = this.processService.isRunningOutOfSync(activatedTag);
    }
  }

  /** Triggers the refreshing of the process status */
  doTriggerProcessStatusUpdate() {
    this.lastAutoRefresh = Date.now();
    this.processService.refreshStatus(this.groupParam, this.uuidParam);
  }

  /** Executes the status refresh operation or updates the remaining seconds */
  doUpdateAutoRefreshProgress() {
    const nextRefreshMs = this.lastAutoRefresh + this.AUTO_REFRESH_INTERVAL_SEC * 1000;
    const diff = nextRefreshMs - Date.now();
    this.nextAutoRefreshSec = Math.round(diff / 1000);
    if (this.nextAutoRefreshSec <= 0) {
      this.doTriggerProcessStatusUpdate();
    }
  }

  /** Return a formatted string for this.nextAutoRefreshSec (assumes times less than 10:00 min) */
  public getAutoRefreshSecFormatted(): string {
    const date = new Date(null);
    date.setSeconds(this.nextAutoRefreshSec);
    return date.toISOString().substr(15, 4);
  }

  /** Returns whether or not the auto-refresh UI is visible */
  isAutoRefreshVisible() {
    if (this.editMode) {
      return false;
    }
    return this.sidenavMode === SidenavMode.ProcessStatus || this.sidenavMode === SidenavMode.Versions;
  }

  importInstanceVersion() {
    const config = new MatDialogConfig();
    config.width = '80%';
    config.height = '60%';
    config.data = {
      title: 'Import Instance Version',
      headerMessage: `Import a new instance version from a previously exported instance version. The target server of the imported version is ignored.`,
      url: this.instanceService.getImportUrl(this.groupParam, this.uuidParam),
      mimeTypes: ['application/x-zip-compressed', 'application/zip'],
      mimeTypeErrorMessage: 'Only ZIP files can be uploaded.'
    };
    this.dialog
      .open(FileUploadComponent, config)
      .afterClosed()
      .subscribe(e => {
        this.loadVersions();
      });
  }

  exportInstanceVersion(key: ManifestKey) {
    window.location.href = this.instanceService.getExportUrl(this.groupParam, this.uuidParam, key.tag);
  }
}
