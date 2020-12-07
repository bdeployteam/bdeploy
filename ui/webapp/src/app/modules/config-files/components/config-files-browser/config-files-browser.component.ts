import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { Location } from '@angular/common';
import { Component, OnInit, TemplateRef, ViewContainerRef } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Base64 } from 'js-base64';
import { cloneDeep } from 'lodash-es';
import { forkJoin, Observable, of } from 'rxjs';
import { tap } from 'rxjs/operators';
import { RoutingHistoryService } from 'src/app/modules/core/services/routing-history.service';
import { ProductService } from 'src/app/modules/shared/services/product.service';
import { FileStatusDto, FileStatusType, InstanceConfiguration } from '../../../../models/gen.dtos';
import { Logger, LoggingService } from '../../../core/services/logging.service';
import { InstanceService } from '../../../instance/services/instance.service';
import { MessageBoxMode } from '../../../shared/components/messagebox/messagebox.component';
import { CanComponentDeactivate } from '../../../shared/guards/can-deactivate.guard';
import { MessageboxService } from '../../../shared/services/messagebox.service';

export enum SyncStatusType {
  ADDED = 'ADD',
  CONFLICT = 'CONFLICT',
  MERGED = 'MERGED',
  IN_SYNC = 'IN_SYNC',
}

export class ConfigFileStatus {
  type: FileStatusType;
  content: string;
}

export const EMPTY_CONFIG_FILE_STATUS: ConfigFileStatus = {
  type: null,
  content: null,
};

@Component({
  selector: 'app-config-files-browser',
  templateUrl: './config-files-browser.component.html',
  styleUrls: ['./config-files-browser.component.css'],
})
export class ConfigFilesBrowserComponent implements OnInit, CanComponentDeactivate {
  private log: Logger = this.loggingService.getLogger('ConfigFilesBrowserComponent');

  groupParam: string = this.route.snapshot.paramMap.get('group');
  uuidParam: string = this.route.snapshot.paramMap.get('uuid');
  versionParam: string = this.route.snapshot.paramMap.get('version');

  public instanceVersion: InstanceConfiguration;

  public get displayedColumns(): string[] {
    if (this.syncMode) {
      return ['sync', 'icon', 'path', 'delete', 'edit'];
    } else {
      return ['icon', 'path', 'delete', 'copy', 'edit'];
    }
  }
  public statusCache = new Map<string, ConfigFileStatus>();
  public typeCache = new Map<string, boolean>();

  public editMode = false; // switching edit/list mode
  public editText = true; // whether the edited file is text

  public editKey: string = null; // original config file name on edit
  public editorContent = '';
  public editorPath = '';

  public conflictStatusCache = new Map<string, string>(); // path -> content
  public conflictTypeCache = new Map<string, boolean>(); // path -> isText
  public syncStatus = new Map<string, SyncStatusType>();
  public syncMode = false;

  public originalContentCache = new Map<string, string>();
  private overlayRef: OverlayRef;
  public dropZoneActive = false;
  public fileUploadChosen = false;

  constructor(
    private route: ActivatedRoute,
    private instanceService: InstanceService,
    private productService: ProductService,
    private loggingService: LoggingService,
    public location: Location,
    private messageBoxService: MessageboxService,
    private overlay: Overlay,
    private viewContainerRef: ViewContainerRef,
    public routingHistoryService: RoutingHistoryService
  ) {}

  public ngOnInit(): void {
    // get instance version
    this.instanceService
      .getInstanceVersion(this.groupParam, this.uuidParam, this.versionParam)
      .subscribe((instanceVersion) => {
        this.instanceVersion = instanceVersion;
      });

    // get list of config files
    this.reload();
  }

  private reload() {
    this.statusCache.clear();
    this.typeCache.clear();

    this.instanceService
      .listConfigurationFiles(this.groupParam, this.uuidParam, this.versionParam)
      .subscribe((configFilePaths) => {
        configFilePaths.forEach((p) => {
          this.statusCache.set(p.path, cloneDeep(EMPTY_CONFIG_FILE_STATUS));
          this.typeCache.set(p.path, p.isText);
        });
      });
  }

  isNameDuplicateError() {
    const cached = this.statusCache.get(this.editorPath);
    return cached && this.editorPath !== this.editKey;
  }

  public listConfigFiles(): string[] {
    return Array.from(this.statusCache.keys());
  }

  public addFile(): void {
    this.editKey = null;
    this.editorPath = '';
    this.editorContent = '';
    this.editMode = true;
  }

  public editFile(path: string, copy: boolean): void {
    const cached = this.statusCache.get(path);
    this.editKey = copy ? null : path;
    this.editMode = true;
    this.editText = this.typeCache.get(path);

    const initialName = copy ? path + ' (copy)' : path;
    if (cached.content) {
      this.editorPath = initialName;
      this.editorContent = cached.content;
    } else {
      this.instanceService
        .getConfigurationFile(this.groupParam, this.uuidParam, this.versionParam, path)
        .subscribe((content) => {
          if (this.typeCache.get(path)) {
            content = Base64.decode(content);
          }
          this.editorPath = initialName;
          this.editorContent = content;
          this.originalContentCache.set(path, content);
        });
    }
  }

  public onApplyChanges() {
    if (this.editKey === null) {
      // new file
      const status = cloneDeep(EMPTY_CONFIG_FILE_STATUS);
      status.type = FileStatusType.ADD;
      status.content = this.editorContent;
      this.statusCache.set(this.editorPath, status);
      this.typeCache.set(this.editorPath, this.editText);
    } else {
      const cached = this.statusCache.get(this.editKey);
      if (this.editKey === this.editorPath) {
        // file content changed?
        const originalContent = this.originalContentCache.get(this.editKey);
        if (this.editorContent === originalContent) {
          cached.type = null;
        } else if (!cached.type) {
          // set if unset, keep ADD, can't be DELETE
          cached.type = FileStatusType.EDIT;
        }
        cached.content = this.editorContent;
      } else {
        // file renamed -- delete old, create a new
        if (cached.type === FileStatusType.ADD) {
          this.statusCache.delete(this.editKey);
        } else {
          cached.type = FileStatusType.DELETE;
        }
        const status = cloneDeep(EMPTY_CONFIG_FILE_STATUS);
        status.type = FileStatusType.ADD;
        status.content = this.editorContent;
        this.statusCache.set(this.editorPath, status);
      }
    }
    this.resetEdit();
  }

  async onCancelChanges(): Promise<void> {
    if (!this.isEditorDirty()) {
      this.resetEdit();
    } else {
      const result = await this.messageBoxService.openAsync({
        title: 'Cancel',
        message: 'Are you sure?',
        mode: MessageBoxMode.QUESTION,
      });

      if (!result) {
        return;
      }
      this.resetEdit();
    }
  }

  private resetEdit(): void {
    this.editMode = false;
    this.editKey = null;
    this.editorPath = '';
    this.editorContent = '';
  }

  public deleteFile(path: string): void {
    const cached = this.statusCache.get(path);
    if (cached.type === FileStatusType.ADD) {
      this.statusCache.delete(path);
    } else {
      cached.type = FileStatusType.DELETE;
      const syncStatus: SyncStatusType = this.syncStatus.get(path);
      if (syncStatus && syncStatus === SyncStatusType.CONFLICT) {
        this.syncStatus.set(path, SyncStatusType.MERGED);
      }
    }
  }

  public restoreFile(path: string): void {
    const cached = this.statusCache.get(path);
    const originalContent = this.originalContentCache.get(path);
    if (cached.content === null || cached.content === originalContent) {
      cached.type = null;
      const syncStatus: SyncStatusType = this.syncStatus.get(path);
      if (syncStatus && syncStatus === SyncStatusType.MERGED) {
        this.syncStatus.set(path, SyncStatusType.CONFLICT);
      }
    } else {
      cached.type = FileStatusType.EDIT;
    }
  }

  public isModified(path: string) {
    const cached = this.statusCache.get(path);
    return cached.type === FileStatusType.EDIT;
  }

  public isNew(path: string) {
    const cached = this.statusCache.get(path);
    return cached.type === FileStatusType.ADD;
  }

  public isText(path: string) {
    return this.typeCache.get(path);
  }

  public isDeleted(path: string) {
    const action: ConfigFileStatus = this.statusCache.get(path);
    return action && action.type === FileStatusType.DELETE;
  }

  public isEditorDirty(): boolean {
    // editor for new file is always dirty
    if (this.editKey === null) {
      return true;
    }
    // rename? -> dirty
    if (this.editKey !== this.editorPath) {
      return true;
    }
    // compare content
    const originalContent = this.originalContentCache.get(this.editKey);
    if (this.editorContent !== originalContent) {
      return true;
    }
    return false;
  }

  public isDirty(): boolean {
    const values: ConfigFileStatus[] = Array.from(this.statusCache.values());
    let changeCount = 0;
    values.forEach((v) => {
      changeCount += v.type ? 1 : 0;
    });
    return changeCount > 0;
  }

  canDeactivate(): Observable<boolean> {
    if (!this.isDirty()) {
      return of(true);
    }
    return this.messageBoxService.open({
      title: 'Unsaved changes',
      message: 'Configuration files were modified. Close without saving?',
      mode: MessageBoxMode.CONFIRM_WARNING,
    });
  }

  public onSave(): void {
    const result: FileStatusDto[] = [];
    const keys: string[] = Array.from(this.statusCache.keys());
    keys.forEach((key) => {
      const value: ConfigFileStatus = this.statusCache.get(key);
      if (!value.type) {
        return; // no update, don't send one.
      }

      let c = value.content;
      if (this.typeCache.get(key)) {
        // it's text, re-encode. otherwise it is binary and already encoded.
        c = Base64.encode(c);
      }

      const dto: FileStatusDto = {
        type: value.type,
        content: value.type === FileStatusType.DELETE ? null : c,
        file: key,
      };

      result.push(dto);
    });

    this.instanceService
      .updateConfigurationFiles(this.groupParam, this.uuidParam, this.versionParam, result)
      .subscribe((_) => {
        this.statusCache.clear(); // avoid isDirty
        this.log.info(
          'stored configuration files for ' + this.groupParam + ', ' + this.uuidParam + ', ' + this.versionParam
        );
        this.location.back();
      });
  }

  /** Opens a modal overlay popup showing the given template */
  openOverlay(template: TemplateRef<any>) {
    this.fileUploadChosen = false;
    this.closeOverlay();

    this.overlayRef = this.overlay.create({
      positionStrategy: this.overlay.position().global().centerHorizontally().centerVertically(),
      hasBackdrop: true,
      disposeOnNavigation: true,
    });

    const portal = new TemplatePortal(template, this.viewContainerRef);
    this.overlayRef.attach(portal);
  }

  /** Closes the overlay if present */
  closeOverlay() {
    if (this.overlayRef) {
      this.overlayRef.detach();
      this.overlayRef.dispose();
      this.overlayRef = null;
    }
  }

  dropZoneState($event: boolean) {
    this.dropZoneActive = $event;
  }

  handleChange(fileName: HTMLInputElement, input: HTMLInputElement) {
    fileName.value = input.files.item(0).name;
    this.fileUploadChosen = true;
  }

  handleDrop(fileList: FileList, fileName: HTMLInputElement, input: HTMLInputElement) {
    if (fileList.length > 1) {
      this.log.errorWithGuiMessage('Only single file uploads allowed');
      return;
    }
    input.files = fileList;
    this.handleChange(fileName, input);
  }

  createNewFile(fileName: HTMLInputElement, fileInput: HTMLInputElement) {
    const name = fileName.value;

    const reader = new FileReader();
    reader.onload = (ev) => {
      let status = this.statusCache.get(name);

      // We need to handle two cases here.
      // -> New files that are uploaded
      // -> Replacing of existing files by uploading the same file again
      // -> Files in status ADD will remain in ADD if they are uploaded again
      if (!status) {
        status = cloneDeep(EMPTY_CONFIG_FILE_STATUS);
        status.type = FileStatusType.ADD;
        this.statusCache.set(name, status);
      } else if (status.type !== FileStatusType.ADD) {
        status.type = FileStatusType.EDIT;
      }

      const result = reader.result.toString();
      status.content = result.substr(result.indexOf(',') + 1);
      this.typeCache.set(name, false);
      this.closeOverlay();
    };
    reader.readAsDataURL(fileInput.files.item(0));
  }

  public onSynchronize(): void {
    this.syncMode = true;
    forkJoin({
      productConfigs: this.productService.listConfigFiles(this.groupParam, this.instanceVersion.product),
      changedConfigs: this.instanceService.syncConfigurationFiles(
        this.groupParam,
        this.uuidParam,
        this.versionParam,
        this.instanceVersion.product
      ),
    }).subscribe((r) => {
      r.productConfigs.forEach((pc) => {
        this.syncStatus.set(pc.path, SyncStatusType.IN_SYNC);
      });
      const calls = [];
      r.changedConfigs.forEach((c) => {
        calls.push(
          this.productService.getConfigurationFile(this.groupParam, this.instanceVersion.product, c.path).pipe(
            tap((f) => {
              if (!this.statusCache.get(c.path)) {
                // file unknown -> new in product (or previously deleted in instance)
                this.syncStatus.set(c.path, SyncStatusType.ADDED);
                const status: ConfigFileStatus = cloneDeep(EMPTY_CONFIG_FILE_STATUS);
                status.type = FileStatusType.ADD;
                status.content = c.isText ? Base64.decode(f) : f;
                this.statusCache.set(c.path, status);
                this.typeCache.set(c.path, c.isText);
              } else {
                // file known, but content differs
                this.syncStatus.set(c.path, SyncStatusType.CONFLICT);
                this.conflictStatusCache.set(c.path, c.isText ? Base64.decode(f) : f);
                this.conflictTypeCache.set(c.path, c.isText);
              }
            })
          )
        );
      });
      forkJoin(calls).subscribe();
    });
  }

  public onFinishSynchronization(): void {
    this.conflictStatusCache.clear();
    this.conflictTypeCache.clear();
    this.syncStatus.clear();
    this.syncMode = false;
  }

  public hasConflicts(): boolean {
    return Array.from(this.syncStatus.values()).some((v) => v === SyncStatusType.CONFLICT);
  }

  public hasConflict(path: string): boolean {
    return this.syncStatus?.get(path) === SyncStatusType.CONFLICT;
  }

  public acceptProductFile(path: string): void {
    this.editorContent = this.conflictStatusCache.get(path);
    this.acceptEditorContent(path);
  }

  public acceptInstanceFile(path: string): void {
    this.acceptEditorContent(path);
  }

  private acceptEditorContent(path: string): void {
    this.syncStatus.set(path, SyncStatusType.MERGED);
    this.onApplyChanges();
  }

  public getSyncTooltip(path: string): string {
    if (this.syncStatus.get(path) == SyncStatusType.ADDED) {
      return 'This file was added because it\'s missing in the instance but is shipped with the product.';
    } else if (this.syncStatus.get(path) == SyncStatusType.CONFLICT) {
      return 'The file of the instance is different from the file that is shipped with the product and needs to be merged manually!';
    } else if (this.syncStatus.get(path) == SyncStatusType.MERGED) {
      return 'The file of the instance was different from the file that is shipped with the product and already merged manually.';
    } else if (this.syncStatus.get(path) == SyncStatusType.IN_SYNC) {
      return 'The file of the instance is equal to the original file that is shipped with the product.';
    } else {
      return 'The file is not shipped with the product.';
    }
  }

  public getSyncIcon(path: string): string {
    if (this.syncStatus.get(path) == SyncStatusType.ADDED) {
      return 'add';
    } else if (this.syncStatus.get(path) == SyncStatusType.CONFLICT) {
      return 'error';
    } else if (this.syncStatus.get(path) == SyncStatusType.MERGED) {
      return 'done_outline';
    } else if (this.syncStatus.get(path) == SyncStatusType.IN_SYNC) {
      return 'done';
    } else {
      return '';
    }
  }

  public isEditButtonDisabled(path: string): boolean {
    return (
      this.syncMode && (this.syncStatus.get(path) === undefined || this.syncStatus.get(path) === SyncStatusType.IN_SYNC)
    );
  }

  public isDeleteButtonDisabled(path: string): boolean {
    return (
      this.syncMode && (this.syncStatus.get(path) === undefined || this.syncStatus.get(path) === SyncStatusType.IN_SYNC)
    );
  }
}
