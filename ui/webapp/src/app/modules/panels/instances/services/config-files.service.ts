import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, combineLatest, Observable, of } from 'rxjs';
import { map } from 'rxjs/operators';
import { ConfigFileDto, FileStatusDto, FileStatusType, InstanceDto, ManifestKey } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { GlobalEditState, InstanceEditService } from 'src/app/modules/primary/instances/services/instance-edit.service';

export interface ConfigFile {
  persistent: ConfigFileDto;
  modification: FileStatusDto;
}

@Injectable({
  providedIn: 'root',
})
export class ConfigFilesService {
  private persistent$ = new BehaviorSubject<ConfigFileDto[]>(null);
  private binCache: { [key: string]: boolean } = {};
  private moveCache: { [key: string]: boolean } = {};

  public files$ = new BehaviorSubject<ConfigFile[]>(null);

  private apiPath = (g, i) => `${this.cfg.config.api}/group/${g}/instance/${i}/cfgFiles`;

  constructor(private http: HttpClient, private cfg: ConfigService, private groups: GroupsService, private editSvc: InstanceEditService) {
    combineLatest([this.persistent$, this.editSvc.state$]).subscribe(([pers, state]) => {
      if (!pers || !state) {
        this.files$.next(null);
      } else {
        // map both to ConfigFile, potentially associating modification with an existing file.
        const m = !!state.files ? state.files : [];
        const p = !!pers ? pers : [];

        const modFiles = m.map((m) => ({ persistent: pers.find((x) => x.path === m.file), modification: m }));
        const persFiles = p.map((p) => ({ persistent: p, modification: m.find((x) => x.file === p.path) }));

        this.files$.next([...persFiles, ...modFiles.filter((x) => !x.persistent)]);
      }
    });

    combineLatest([this.groups.current$, this.editSvc.current$, this.editSvc.state$]).subscribe(([group, instance, state]) => {
      if (!group || !instance || !state || !state.config) {
        return;
      }
      this.loadFiles(group.name, instance, state).subscribe((f) => this.persistent$.next(f));
    });

    this.editSvc.saving$.subscribe((s) => {
      if (s) {
        this.binCache = {};
        this.moveCache = {};
      }
    });
  }

  private loadFiles(group: string, instance: InstanceDto, state: GlobalEditState): Observable<ConfigFileDto[]> {
    return this.http.get<ConfigFileDto[]>(
      `${this.apiPath(group, instance.instanceConfiguration.uuid)}/${instance.instance.tag}/${state.config.config.product.name}/${
        state.config.config.product.tag
      }`
    );
  }

  public getPath(f: ConfigFile) {
    return f.persistent?.path ? f.persistent.path : f.modification.file;
  }

  public get(path: string): ConfigFile {
    return this.files$.value?.find((f) => f.persistent?.path === path || f.modification?.file === path);
  }

  private internalAdd(path: string, content: string, binary: boolean) {
    const existing = this.get(path);
    if (!!existing?.persistent?.instanceId || !!existing?.modification?.file) {
      throw new Error('Cannot add file: Already have a file with this name: ' + path);
    }

    const file: FileStatusDto = { content: content, file: path, type: FileStatusType.ADD };
    this.binCache[path] = binary;

    this.editSvc.state$.value.files.push(file);
  }

  private internalDelete(path: string) {
    // find any existing modification and remove it.
    const mod = this.editSvc.state$.value.files?.find((m) => m.file === path);
    if (!!mod) {
      this.editSvc.state$.value.files.splice(this.editSvc.state$.value.files.indexOf(mod), 1);
    }

    const f = this.get(path);

    if (!!f.persistent?.instanceId) {
      // only need to tell the server to delete if the file is not new.
      this.editSvc.state$.value.files.push({ file: path, content: null, type: FileStatusType.DELETE });
    }
    delete this.binCache[path];
    delete this.moveCache[path];
  }

  public add(path: string, content: string, binary: boolean) {
    this.internalAdd(path, content, binary);
    this.editSvc.conceal(`Add file: ${path}`);
  }

  public delete(path: string) {
    this.internalDelete(path);
    this.editSvc.conceal(`Delete file: ${path}`);
  }

  public move(path: string, newPath: string, content: string, binary: boolean) {
    const isMove = !!this.get(path).persistent;

    this.internalDelete(path);
    this.internalAdd(newPath, content, binary);
    this.editSvc.conceal(`Move ${path} to ${newPath}`);

    this.moveCache[newPath] = isMove;
    delete this.moveCache[path];
  }

  public edit(path: string, content: string) {
    // find any existing modification and remove it.
    const mod = this.editSvc.state$.value.files?.find((m) => m.file === path);
    if (!!mod) {
      this.editSvc.state$.value.files.splice(this.editSvc.state$.value.files.indexOf(mod), 1);
    }

    const edit = {
      content: content,
      file: path,
      type: mod?.type === FileStatusType.ADD ? FileStatusType.ADD : FileStatusType.EDIT,
    };

    this.editSvc.state$.value.files.push(edit);
    this.editSvc.conceal(`Edit file: ${path}`);
  }

  public load(path: string): Observable<string> {
    const f = this.get(path);
    if (!f) return of(null);
    if (!!f.modification?.file || !f.persistent) {
      return of(f.modification?.content);
    }

    return this.http
      .get(
        `${this.apiPath(this.groups.current$.value.name, this.editSvc.current$.value.instanceConfiguration.uuid)}/load/${
          this.editSvc.current$.value.instance.tag
        }/${path}`,
        { responseType: 'text' }
      )
      .pipe(map((s) => s, measure(`Load ${path}`)));
  }

  public loadTemplate(path: string, product: ManifestKey): Observable<string> {
    return this.http
      .get(`${this.apiPath(this.groups.current$.value.name, this.editSvc.current$.value.instanceConfiguration.uuid)}/loadTemplate/${path}`, {
        responseType: 'text',
        params: { prodName: product.name, prodTag: product.tag },
      })
      .pipe(map((s) => s, measure(`Load Template ${path}`)));
  }

  public isText(f: ConfigFile) {
    if (!!f.persistent?.isText) {
      return true;
    }

    if (!f.persistent) {
      if (!f.modification?.content) {
        return true; // literally no content, empty file -> text.
      }

      const path = this.getPath(f);

      if (this.binCache[path] !== undefined) {
        return !this.binCache[path];
      }
    }

    return false;
  }

  public isMoved(f: ConfigFile) {
    return !!this.moveCache[this.getPath(f)];
  }
}
