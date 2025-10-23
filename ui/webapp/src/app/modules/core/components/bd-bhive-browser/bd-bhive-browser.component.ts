import { Component, inject, OnDestroy, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { Sort } from '@angular/material/sort';
import { ActivatedRoute, ActivatedRouteSnapshot, Router } from '@angular/router';
import { Base64 } from 'js-base64';
import { BehaviorSubject, combineLatest, map, Observable, Subscription } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { HiveEntryDto, TreeEntryType } from 'src/app/models/gen.dtos';
import { CrumbInfo } from 'src/app/modules/core/components/bd-breadcrumbs/bd-breadcrumbs.component';
import { BdDataIconCellComponent } from 'src/app/modules/core/components/bd-data-icon-cell/bd-data-icon-cell.component';
import { BdDataSizeCellComponent } from 'src/app/modules/core/components/bd-data-size-cell/bd-data-size-cell.component';
import { ACTION_CLOSE } from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { SearchService } from 'src/app/modules/core/services/search.service';
import { HiveService } from 'src/app/modules/primary/admin/services/hive.service';
import { RepositoriesService } from 'src/app/modules/primary/repositories/services/repositories.service';
import { DownloadService } from '../../services/download.service';
import { BdManifestDeleteActionComponent } from './bd-manifest-delete-action/bd-manifest-delete-action.component';
import { BdEditorComponent } from '../bd-editor/bd-editor.component';

import { BdDialogToolbarComponent } from '../bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdButtonComponent } from '../bd-button/bd-button.component';
import { MatDivider } from '@angular/material/divider';
import { BdDialogContentComponent } from '../bd-dialog-content/bd-dialog-content.component';
import { BdBreadcrumbsComponent } from '../bd-breadcrumbs/bd-breadcrumbs.component';
import { BdDataTableComponent } from '../bd-data-table/bd-data-table.component';
import { AsyncPipe } from '@angular/common';

interface BHivePathSegment {
  name: string;
  tag?: string;
  id?: string;
}

@Component({
  selector: 'app-bd-bhive-browser',
  templateUrl: './bd-bhive-browser.component.html',
  imports: [
    BdEditorComponent,
    BdDialogComponent,
    BdDialogToolbarComponent,
    BdButtonComponent,
    MatDivider,
    BdDialogContentComponent,
    BdBreadcrumbsComponent,
    BdDataTableComponent,
    AsyncPipe,
  ],
})
export class BdBHiveBrowserComponent implements OnInit, OnDestroy {
  private readonly areas = inject(NavAreasService);
  private readonly search = inject(SearchService);
  private readonly router = inject(Router);
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly downloads = inject(DownloadService);
  protected readonly hives = inject(HiveService);
  protected readonly repositories = inject(RepositoriesService);

  private readonly colAvatar: BdDataColumn<HiveEntryDto, string> = {
    id: 'avatar',
    name: '',
    data: (r) => this.getImage(r),
    width: '40px',
    component: BdDataIconCellComponent
  };

  private readonly colName: BdDataColumn<HiveEntryDto, string> = {
    id: 'name',
    name: 'Name',
    data: (r) => r.name,
    isId: true
  };

  private readonly colSize: BdDataColumn<HiveEntryDto, number> = {
    id: 'size',
    name: 'Size',
    data: (r) => (r.size > 0 ? r.size : null),
    width: '120px',
    component: BdDataSizeCellComponent
  };

  private readonly colDownload: BdDataColumn<HiveEntryDto, string> = {
    id: 'download',
    name: 'Downl.',
    data: () => 'Download',
    action: (r) => this.doDownload(r),
    icon: () => 'cloud_download',
    width: '50px'
  };

  private readonly colDelete: BdDataColumn<HiveEntryDto, string> = {
    id: 'delete',
    name: 'Delete',
    data: (r) => `Delete ${r.name}`,
    width: '40px',
    component: BdManifestDeleteActionComponent
  };

  public bhive$ = new BehaviorSubject<string>(null);
  protected path$ = new BehaviorSubject<BHivePathSegment[]>(null);
  protected entries$ = new BehaviorSubject<HiveEntryDto[]>([]);
  protected columns = [this.colAvatar, this.colName, this.colSize, this.colDownload, this.colDelete];
  protected sort: Sort = { active: 'name', direction: 'asc' };

  protected previewContent$ = new BehaviorSubject<string>(null);
  protected previewName$ = new BehaviorSubject<string>(null);

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild('previewTemplate') private readonly previewTemplate: TemplateRef<unknown>;

  private subscription: Subscription;
  private lastQuery: string;

  protected type: 'bhive' | 'repo' | 'product' | undefined;

  ngOnInit() {
    this.subscription = combineLatest([this.areas.primaryRoute$, this.areas.panelRoute$]).subscribe(
      ([primaryRoute, panelRoute]) => {
        if (!primaryRoute || !panelRoute?.params?.['type']) {
          return;
        }

        this.type = panelRoute.params['type'];

        const hive = this.getBHive(primaryRoute, panelRoute);

        if (!hive) {
          return;
        }

        this.bhive$.next(hive);

        if (panelRoute.queryParams['q']) {
          if (this.lastQuery !== panelRoute.queryParams['q']) {
            this.lastQuery = panelRoute.queryParams['q'];
            this.path$.next(this.decodePathForUrl(panelRoute.queryParams['q']));

            // clear out previous search in case we changed paths.
            this.search.search = '';
          }
        } else {
          this.lastQuery = null;
          this.path$.next(this.getRootPath(panelRoute));
        }

        this.load();
      }
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  private getRootPath(panelRoute: ActivatedRouteSnapshot): BHivePathSegment[] {
    if (this.type === 'bhive') {
      return null;
    } else if (this.type === 'repo' || this.type === 'product') {
      const name = panelRoute.params['key'];
      const tag = panelRoute.params['tag'];
      return [{ name, tag }];
    }

    throw new Error(`Unexpected type ${this.type}`);
  }

  private getBHive(primaryRoute: ActivatedRouteSnapshot, panelRoute: ActivatedRouteSnapshot): string {
    if (this.type === 'bhive') {
      return panelRoute.params['bhive'];
    } else if (this.type === 'repo') {
      return primaryRoute.params['repository'];
    } else if (this.type === 'product') {
      return primaryRoute.params['group'];
    } else {
      throw new Error(`Unexpected bhive browser type ${this.type}`);
    }
  }

  public load() {
    if (!this.path$.value?.length) {
      this.hives.listManifests(this.bhive$.value).subscribe((manifests) => {
        this.entries$.next(manifests);
      });
    } else {
      const lastSegment = this.path$.value.at(-1);
      if (lastSegment?.id) {
        this.hives.list(this.bhive$.value, lastSegment.id).subscribe((entries) => {
          this.entries$.next(entries);
        });
      } else {
        this.hives.listManifest(this.bhive$.value, lastSegment.name, lastSegment.tag).subscribe((entries) => {
          this.entries$.next(entries);
        });
      }
    }
  }

  private getImage(row: HiveEntryDto) {
    switch (row.type) {
      case TreeEntryType.MANIFEST:
        return 'folder_special';
      case TreeEntryType.BLOB:
        return 'insert_drive_file';
      case TreeEntryType.TREE:
        return 'folder';
    }
  }

  private encodePathForUrl(pathSegments: BHivePathSegment[]): string {
    if (!pathSegments?.length) {
      return null;
    }
    const allStrings: string[] = pathSegments.map((s) => `|[${s.name}|${s.tag ? s.tag : ''}|${s.id ? s.id : ''}]|`);
    return Base64.encode(JSON.stringify(allStrings), true);
  }

  private decodePathForUrl(encodedPath: string): BHivePathSegment[] {
    const decoded = Base64.decode(encodedPath);
    const allStrings: string[] = JSON.parse(decoded);
    return allStrings
      .filter((segmentAsString) => segmentAsString.startsWith('|[') && segmentAsString.endsWith(']|'))
      .map((segmentAsString) => {
        const parts = segmentAsString.substring(2, segmentAsString.length - 2).split('|');
        return {
          name: parts[0],
          tag: parts[1]?.length ? parts[1] : undefined,
          id: parts[2]?.length ? parts[2] : undefined
        };
      });
  }

  private showPreviewIfText(data: Blob, name: string) {
    const reader = new FileReader();
    reader.onload = () => {
      const result = reader.result.toString();

      // extract the base64 part of the data URL...
      const base64Content = result.substr(result.indexOf(',') + 1);

      const buffer = Base64.toUint8Array(base64Content);
      let isBinary = false;
      for (let i = 0; i < Math.max(4096, buffer.length); ++i) {
        if (buffer[i] === 0) {
          isBinary = true;
          break;
        }
      }

      if (!isBinary) {
        this.previewName$.next(name);
        this.previewContent$.next(Base64.decode(base64Content));
        this.dialog
          .message({
            header: `Preview ${name}`,
            template: this.previewTemplate,
            actions: [ACTION_CLOSE]
          })
          .subscribe();
      } else {
        this.dialog.info(`Preview ${name}`, `${name} is binary data and cannot be previewed.`).subscribe();
      }
    };
    reader.readAsDataURL(data);
  }

  protected onClick(r: HiveEntryDto) {
    // need to handle this manually without recordRoute for history and queryParam handling.

    const path = this.path$.value?.length ? [...this.path$.value] : [];

    if (r.type === TreeEntryType.MANIFEST) {
      path.push({ name: r.mName, tag: r.mTag });
    } else if (r.type === TreeEntryType.TREE) {
      path.push({ name: r.name, id: r.id });
    } else if (r.size <= 1024 * 1024 * 1024 /* limit for inline viewing of files */) {
      this.hives.download(this.bhive$.value, r.id).subscribe((data) => {
        this.showPreviewIfText(data, r.name);
      });
    } else {
      this.dialog.info(`Preview ${r.name}`, `${r.name} is too large to preview.`).subscribe();
    }

    this.router.navigate([], {
      relativeTo: this.activatedRoute,
      queryParams: { q: this.encodePathForUrl(path) }
    });
  }

  protected onNavigateUp() {
    if (this.path$.value?.length) {
      const path = [...this.path$.value];
      path.pop();
      this.navigateTo(path);
    }
  }

  protected get crumbs$(): Observable<CrumbInfo[]> {
    return this.path$.pipe(
      map((segments: BHivePathSegment[]) => {
        const acc: BHivePathSegment[] = [];
        const crumbs = (segments || []).map((s: BHivePathSegment) => {
          acc.push(s);
          const path = [...acc];
          const label = `${s.name}${s.tag ? ':' + s.tag : ''}`;
          const onClick = () => this.navigateTo(path);
          return { label, onClick };
        });
        const root = { label: this.bhive$.value, onClick: () => this.navigateTo(null) };
        return this.type === 'bhive' ? [root, ...crumbs] : crumbs;
      })
    );
  }

  private navigateTo(path: BHivePathSegment[]) {
    this.router.navigate([], {
      relativeTo: this.activatedRoute,
      queryParams: { q: this.encodePathForUrl(path) }
    });
  }

  private doDownload(e: HiveEntryDto) {
    const name = e.type === TreeEntryType.BLOB ? e.name : `${e.name}.zip`;
    this.hives.downloadContent(this.bhive$.value, e).subscribe((blob) => this.downloads.downloadBlob(name, blob));
  }

  protected downloadAll() {
    const s = this.path$.value.at(-1);
    const dto: HiveEntryDto = {
      id: s.id,
      mName: s.name,
      mTag: s.tag,
      name: s.tag ? `${s.name}:${s.tag}` : s.name,
      type: s.id ? TreeEntryType.TREE : TreeEntryType.MANIFEST,
      size: null
    };
    this.doDownload(dto);
  }
}
