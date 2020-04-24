import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { Location } from '@angular/common';
import { Component, OnDestroy, OnInit, TemplateRef, ViewContainerRef } from '@angular/core';
import { AbstractControl, FormBuilder, FormControl, ValidatorFn, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import 'brace/mode/batchfile';
import 'brace/mode/json';
import 'brace/mode/sh';
import 'brace/mode/text';
import 'brace/mode/xml';
import 'brace/mode/yaml';
import 'brace/theme/eclipse';
import 'brace/theme/twilight';
import { Base64 } from 'js-base64';
import { cloneDeep } from 'lodash';
import { Observable, of, Subscription } from 'rxjs';
import { FileStatusDto, FileStatusType, InstanceConfiguration } from '../../../../models/gen.dtos';
import { Logger, LoggingService } from '../../../core/services/logging.service';
import { ThemeService } from '../../../core/services/theme.service';
import { InstanceService } from '../../../instance/services/instance.service';
import { MessageBoxMode } from '../../../shared/components/messagebox/messagebox.component';
import { CanComponentDeactivate } from '../../../shared/guards/can-deactivate.guard';
import { MessageboxService } from '../../../shared/services/messagebox.service';

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
export class ConfigFilesBrowserComponent implements OnInit, OnDestroy, CanComponentDeactivate {
  private log: Logger = this.loggingService.getLogger('ConfigFilesBrowserComponent');

  groupParam: string = this.route.snapshot.paramMap.get('group');
  uuidParam: string = this.route.snapshot.paramMap.get('uuid');
  versionParam: string = this.route.snapshot.paramMap.get('version');

  private themeSubscription: Subscription;

  public instanceVersion: InstanceConfiguration;

  public displayedColumns: string[] = ['icon', 'path', 'delete', 'copy', 'edit'];
  public statusCache = new Map<string, ConfigFileStatus>();
  public typeCache = new Map<string, boolean>();

  // used in edit mode:
  public editMode = false; // switching edit/list mode
  public editText = true; // whether the edited file is text

  public editKey: string = null; // original config file name on edit
  public configFileFormGroup = this.fb.group({
    path: ['', [Validators.required, this.duplicateNameValidator()]],
  });
  public editorContent = '';
  public editorMode = 'text';
  private editorModeMap = new Map([
    ['json', 'json'],
    ['xml', 'xml'],
    ['bat', 'batchfile'],
    ['yaml', 'yaml'],
    ['sh', 'sh'],
  ]);
  public editorTheme = '';

  get pathControl() {
    return this.configFileFormGroup.get('path');
  }

  public originalContentCache = new Map<string, string>();
  private overlayRef: OverlayRef;
  public dropZoneActive = false;
  public fileUploadChosen = false;

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private instanceService: InstanceService,
    private loggingService: LoggingService,
    public location: Location,
    private themeService: ThemeService,
    private messageBoxService: MessageboxService,
    private overlay: Overlay,
    private viewContainerRef: ViewContainerRef,
  ) {}

  public ngOnInit(): void {
    // get instance version
    this.instanceService
      .getInstanceVersion(this.groupParam, this.uuidParam, this.versionParam)
      .subscribe(instanceVersion => {
        this.instanceVersion = instanceVersion;
      });

    // get list of config files
    this.reload();

    this.themeSubscription = this.themeService.getThemeSubject().subscribe(theme => {
      this.editorTheme = this.themeService.getAceTheme();
    });

    this.pathControl.valueChanges.subscribe(e => {
      const regex = /(?:\.([^.]+))?$/;
      const ext = regex.exec(e)[1];
      if (ext) {
        const newMode: string = this.editorModeMap.get(ext);
        if (newMode) {
          this.editorMode = newMode;
          return;
        }
      }
      this.editorMode = 'text';
    });
  }

  private reload() {
    this.statusCache.clear();
    this.typeCache.clear();

    this.instanceService
      .listConfigurationFiles(this.groupParam, this.uuidParam, this.versionParam)
      .subscribe(configFilePaths => {
        configFilePaths.forEach(p => {
          this.statusCache.set(p.path, cloneDeep(EMPTY_CONFIG_FILE_STATUS));
          this.typeCache.set(p.path, p.isText);
        });
      });
  }

  public ngOnDestroy(): void {
    this.themeSubscription.unsubscribe();
  }

  public duplicateNameValidator(): ValidatorFn {
    return (control: AbstractControl): { [key: string]: any } | null => {
      const cached = this.statusCache.get(control.value);
      const duplicate = cached && control.value !== this.editKey;
      return duplicate ? { duplicate: { value: control.value } } : null;
    };
  }

  public getErrorMessage(ctrl: FormControl): string {
    if (ctrl.hasError('required')) {
      return 'Required';
    } else if (ctrl.hasError('duplicate')) {
      return 'File already exists';
    }
    return 'Unknown error';
  }

  public listConfigFiles(): string[] {
    return Array.from(this.statusCache.keys());
  }

  public addFile(): void {
    this.editKey = null;
    this.configFileFormGroup.setValue({ path: '' });
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
      this.configFileFormGroup.setValue({ path: initialName });
      this.editorContent = cached.content;
    } else {
      this.instanceService
        .getConfigurationFile(this.groupParam, this.uuidParam, this.versionParam, path)
        .subscribe(content => {
          if (this.typeCache.get(path)) {
            content = Base64.decode(content);
          }
          this.configFileFormGroup.setValue({ path: initialName });
          this.editorContent = content;
          this.originalContentCache.set(path, content);
        });
    }
  }

  public onApplyChanges() {
    const formValue = this.configFileFormGroup.getRawValue();
    if (this.editKey === null) {
      // new file
      const status = cloneDeep(EMPTY_CONFIG_FILE_STATUS);
      status.type = FileStatusType.ADD;
      status.content = this.editorContent;
      this.statusCache.set(formValue['path'], status);
      this.typeCache.set(formValue['path'], this.editText);
    } else {
      const cached = this.statusCache.get(this.editKey);
      if (this.editKey === formValue['path']) {
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
        this.statusCache.set(formValue['path'], status);
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
    this.configFileFormGroup.reset();
    this.editorContent = '';
  }

  public deleteFile(path: string): void {
    const cached = this.statusCache.get(path);
    if (cached.type === FileStatusType.ADD) {
      this.statusCache.delete(path);
    } else {
      cached.type = FileStatusType.DELETE;
    }
  }

  public restoreFile(path: string): void {
    const cached = this.statusCache.get(path);
    const originalContent = this.originalContentCache.get(path);
    if (cached.content === null || cached.content === originalContent) {
      cached.type = null;
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
    const formValue = this.configFileFormGroup.getRawValue();
    if (this.editKey !== formValue['path']) {
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
    values.forEach(v => {
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
    keys.forEach(key => {
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
      .subscribe(_ => {
        this.statusCache.clear(); // avoid isDirty
        this.log.info(
          'stored configuration files for ' + this.groupParam + ', ' + this.uuidParam + ', ' + this.versionParam,
        );
        this.location.back();
      });
  }

  async onDiscardChanges(): Promise<void> {
    const result = await this.messageBoxService.openAsync({
      title: 'Discard changes',
      message: 'Are you sure you want to discard all local changes?',
      mode: MessageBoxMode.QUESTION,
    });

    if (!result) {
      return;
    }
    this.location.back();
  }

  /** Opens a modal overlay popup showing the given template */
  openOverlay(template: TemplateRef<any>) {
    this.fileUploadChosen = false;
    this.closeOverlay();

    this.overlayRef = this.overlay.create({
      positionStrategy: this.overlay
        .position()
        .global()
        .centerHorizontally()
        .centerVertically(),
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
    reader.onload = ev => {
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
}
