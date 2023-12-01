import { Component, OnDestroy, OnInit, TemplateRef, ViewChild, inject } from '@angular/core';
import { Sort } from '@angular/material/sort';
import { ActivatedRoute, Router } from '@angular/router';
import { Base64 } from 'js-base64';
import { BehaviorSubject, Observable, Subscription, map } from 'rxjs';
import { BdDataColumn } from 'src/app/models/data';
import { HiveEntryDto, ManifestKey, TreeEntryType } from 'src/app/models/gen.dtos';
import { CrumbInfo } from 'src/app/modules/core/components/bd-breadcrumbs/bd-breadcrumbs.component';
import { BdDataIconCellComponent } from 'src/app/modules/core/components/bd-data-icon-cell/bd-data-icon-cell.component';
import { BdDataSizeCellComponent } from 'src/app/modules/core/components/bd-data-size-cell/bd-data-size-cell.component';
import { ACTION_CLOSE } from 'src/app/modules/core/components/bd-dialog-message/bd-dialog-message.component';
import { BdDialogComponent } from 'src/app/modules/core/components/bd-dialog/bd-dialog.component';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { SearchService } from 'src/app/modules/core/services/search.service';
import { HiveService } from 'src/app/modules/primary/admin/services/hive.service';
import { ManifestDeleteActionComponent } from './manifest-delete-action/manifest-delete-action.component';

type BHivePathSegment = string | ManifestKey;

@Component({
  selector: 'app-bhive-browser',
  templateUrl: './bhive-browser.component.html',
})
export class BHiveBrowserComponent implements OnInit, OnDestroy {
  private areas = inject(NavAreasService);
  private search = inject(SearchService);
  private router = inject(Router);
  private activatedRoute = inject(ActivatedRoute);
  protected hives = inject(HiveService);

  private readonly colAvatar: BdDataColumn<HiveEntryDto> = {
    id: 'avatar',
    name: '',
    data: (r) => this.getImage(r),
    width: '40px',
    component: BdDataIconCellComponent,
  };

  private readonly colName: BdDataColumn<HiveEntryDto> = {
    id: 'name',
    name: 'Name',
    data: (r) => r.name,
    isId: true,
  };

  private readonly colSize: BdDataColumn<HiveEntryDto> = {
    id: 'size',
    name: 'Size',
    data: (r) => (r.size > 0 ? r.size : null),
    width: '120px',
    component: BdDataSizeCellComponent,
  };

  private readonly colDelete: BdDataColumn<HiveEntryDto> = {
    id: 'delete',
    name: 'Delete',
    data: (r) => `Delete ${r.name}`,
    width: '40px',
    component: ManifestDeleteActionComponent,
  };

  public bhive$ = new BehaviorSubject<string>(null);
  protected path$ = new BehaviorSubject<BHivePathSegment[]>(null);
  protected entries$ = new BehaviorSubject<HiveEntryDto[]>([]);
  protected columns = [this.colAvatar, this.colName, this.colSize, this.colDelete];
  protected sort: Sort = { active: 'name', direction: 'asc' };

  protected previewContent$ = new BehaviorSubject<string>(null);
  protected previewName$ = new BehaviorSubject<string>(null);

  @ViewChild(BdDialogComponent) public dialog: BdDialogComponent;
  @ViewChild('previewTemplate') private previewTemplate: TemplateRef<any>;

  private subscription: Subscription;
  private lastQuery: string;

  ngOnInit() {
    this.subscription = this.areas.panelRoute$.subscribe((route) => {
      if (!route?.params || !route?.params['bhive']) {
        return;
      }

      this.bhive$.next(route.params['bhive']);
      if (route.queryParams['q']) {
        if (this.lastQuery !== route.queryParams['q']) {
          this.lastQuery = route.queryParams['q'];
          this.path$.next(this.decodePathForUrl(route.queryParams['q']));

          // clear out previous search in case we changed paths.
          this.search.search = '';
        }
      } else {
        this.lastQuery = null;
        this.path$.next(null);
      }

      this.load();
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  public load() {
    if (!this.path$.value?.length) {
      this.hives.listManifests(this.bhive$.value).subscribe((manifests) => {
        this.entries$.next(manifests);
      });
    } else {
      const lastSegment = this.path$.value[this.path$.value.length - 1];
      if (typeof lastSegment === 'string') {
        this.hives.list(this.bhive$.value, lastSegment).subscribe((entries) => {
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

  private encodePathForUrl(path: BHivePathSegment[]): string {
    if (!path?.length) {
      return null;
    }
    const allStrings = path.map((s) => (typeof s === 'string' ? s : `|[${s.name}|${s.tag}]|`));
    return Base64.encode(JSON.stringify(allStrings), true);
  }

  private decodePathForUrl(encodedPath: string): BHivePathSegment[] {
    const decoded = Base64.decode(encodedPath);
    const allStrings = JSON.parse(decoded);
    return allStrings.map((s) => {
      if (s.startsWith('|[') && s.endsWith(']|')) {
        const parts = s.substring(2, s.length - 2).split('|');
        return { name: parts[0], tag: parts[1] };
      }
      return s;
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
            actions: [ACTION_CLOSE],
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
      path.push(r.id);
    } else {
      // limit for inline viewing of files.
      if (r.size <= 1024 * 1024 * 1024) {
        this.hives.download(this.bhive$.value, r.id).subscribe((data) => {
          this.showPreviewIfText(data, r.name);
        });
      } else {
        this.dialog.info(`Preview ${r.name}`, `${r.name} is too large to preview.`).subscribe();
      }
    }

    this.router.navigate([], {
      relativeTo: this.activatedRoute,
      queryParams: { q: this.encodePathForUrl(path) },
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
      map((segments) => {
        const acc = [];
        const crumbs = (segments || []).map((s) => {
          acc.push(s);
          const path = [...acc];
          const label = typeof s !== 'string' ? `${(s as ManifestKey).name}:${(s as ManifestKey).tag}` : s;
          const onClick = () => this.navigateTo(path);
          return { label, onClick };
        });
        const root = { label: this.bhive$.value, onClick: () => this.navigateTo(null) };
        return [root, ...crumbs];
      }),
    );
  }

  private navigateTo(path: BHivePathSegment[]) {
    this.router.navigate([], {
      relativeTo: this.activatedRoute,
      queryParams: { q: this.encodePathForUrl(path) },
    });
  }
}
