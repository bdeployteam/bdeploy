import { Location } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
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
import { cloneDeep } from 'lodash';
import { Subscription } from 'rxjs';
import { FileStatusDto, FileStatusType, InstanceConfiguration } from '../models/gen.dtos';
import { InstanceService } from '../services/instance.service';
import { Logger, LoggingService } from '../services/logging.service';
import { ThemeService } from '../services/theme.service';


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
  styleUrls: ['./config-files-browser.component.css']
})
export class ConfigFilesBrowserComponent implements OnInit, OnDestroy {

  private log: Logger = this.loggingService.getLogger('ConfigFilesBrowserComponent');

  groupParam: string = this.route.snapshot.paramMap.get('group');
  uuidParam: string = this.route.snapshot.paramMap.get('uuid');
  versionParam: string = this.route.snapshot.paramMap.get('version');

  private themeSubscription: Subscription;

  public instanceVersion: InstanceConfiguration;

  public displayedColumns: string[] = ['icon', 'path', 'delete', 'copy', 'edit'];
  public statusCache = new Map<string, ConfigFileStatus>();

  // used in edit mode:
  public editMode = false; // switching edit/list mode

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
    ['sh', 'sh']
  ]);
  public editorTheme = '';

  get pathControl() {
    return this.configFileFormGroup.get('path');
  }

  public originalContentCache = new Map<string, string>();

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private instanceService: InstanceService,
    private loggingService: LoggingService,
    public location: Location,
    private themeService: ThemeService
  ) {}


  public ngOnInit(): void {
    // get instance version
    this.instanceService.getInstanceVersion(this.groupParam, this.uuidParam, this.versionParam).subscribe(
      instanceVersion => {this.instanceVersion = instanceVersion; }
    );

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
    this.instanceService.listConfigurationFiles(this.groupParam, this.uuidParam, this.versionParam).subscribe(configFilePaths => { configFilePaths.forEach(p => this.statusCache.set(p, cloneDeep(EMPTY_CONFIG_FILE_STATUS))); });
  }

  public ngOnDestroy(): void {
    this.themeSubscription.unsubscribe();
  }

  public duplicateNameValidator(): ValidatorFn {
    return (control: AbstractControl): {[key: string]: any} | null => {
      const cached = this.statusCache.get(control.value);
      const duplicate = cached && control.value !== this.editKey;
      return duplicate ? {'duplicate': {value: control.value}} : null;
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
    this.configFileFormGroup.setValue({path: ''});
    this.editorContent = '';
    this.editMode = true;
  }

  public editFile(path: string, copy: boolean): void {
    const cached = this.statusCache.get(path);
    this.editKey = copy ? null : path;
    this.editMode = true;

    const intialName = copy ? path + ' (copy)' : path;
    if (cached.content) {
      this.configFileFormGroup.setValue({path: intialName});
      this.editorContent = cached.content;
    } else {
      this.instanceService.getConfigurationFile(this.groupParam, this.uuidParam, this.versionParam, path).subscribe(
        content => {
          this.configFileFormGroup.setValue({path: intialName});
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
    } else {
      const cached = this.statusCache.get(this.editKey);
      if (this.editKey === formValue['path']) {
        // file content changed?
        const originalContent = this.originalContentCache.get(this.editKey);
        if (this.editorContent === originalContent) {
          cached.type = null;
        } else if (!cached.type) { // set if unset, keep ADD, can't be DELETE
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

  public onCancelChanges() {
    this.resetEdit();
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

  public isDeleted(path: string) {
    const action: ConfigFileStatus = this.statusCache.get(path);
    return action && action.type === FileStatusType.DELETE;
  }

  public isDirty(): boolean {
    const values: ConfigFileStatus[] = Array.from(this.statusCache.values());
    let changeCount = 0;
    values.forEach(v => { changeCount += v.type ? 1 : 0; });
    return changeCount > 0;
  }

  public onSave(): void {
    const result: FileStatusDto[] = [];
    const keys: string[] = Array.from(this.statusCache.keys());
    keys.forEach(key => {
      const value: ConfigFileStatus = this.statusCache.get(key);
      if (!value.type) {
        return; // no update, don't send one.
      }

      const dto: FileStatusDto = {
        type: value.type,
        content: value.type === FileStatusType.DELETE ? null : value.content,
        file: key
      };

      result.push(dto);
    });

    this.instanceService.updateConfigurationFiles(this.groupParam, this.uuidParam, this.versionParam, result)
    .subscribe(_ => {
      this.log.info('stored configuration files for ' + this.groupParam + ', ' + this.uuidParam + ', ' + this.versionParam);
      this.location.back();
    });
  }
}
