import { Location } from '@angular/common';
import { Component, OnInit, ViewChild } from '@angular/core';
import { MatDialog, MatDialogConfig } from '@angular/material/dialog';
import { MatDrawer } from '@angular/material/sidenav';
import { ActivatedRoute } from '@angular/router';
import { forkJoin } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { SoftwarePackageGroup } from 'src/app/models/software.model';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { RoutingHistoryService } from 'src/app/modules/core/services/routing-history.service';
import { ProductService } from 'src/app/modules/shared/services/product.service';
import { ManifestKey, OperatingSystem, ProductDto } from '../../../../models/gen.dtos';
import { Logger, LoggingService } from '../../../core/services/logging.service';
import { SoftwareService } from '../../services/software.service';
import { SoftwareRepoFileUploadComponent } from '../software-repo-file-upload/software-repo-file-upload.component';

@Component({
  selector: 'app-software-repository',
  templateUrl: './software-repository.component.html',
  styleUrls: ['./software-repository.component.css'],
})
export class SoftwareRepositoryComponent implements OnInit {
  private log: Logger = this.loggingService.getLogger('SoftwareRepositoryComponent');

  @ViewChild('appsidenav', { static: true })
  sidenav: MatDrawer;

  public softwareRepositoryName: string = this.route.snapshot.paramMap.get('name');

  public products: Map<string, ProductDto[]> = new Map();
  get productsKeys(): string[] {
    return Array.from(this.products.keys());
  }

  public externalPackageGroups: Map<string, SoftwarePackageGroup> = new Map();
  get externalPackageValues() {
    return Array.from(this.externalPackageGroups.values());
  }

  public selection: string;

  activeOs: OperatingSystem = OperatingSystem.UNKNOWN;

  loading = false;

  constructor(
    private route: ActivatedRoute,
    private loggingService: LoggingService,
    public location: Location,
    private productService: ProductService,
    private softwareService: SoftwareService,
    public dialog: MatDialog,
    private authService: AuthenticationService,
    public routingHistoryService: RoutingHistoryService
  ) {}

  ngOnInit() {
    this.load();
    this.activeOs = this.softwareService.getRunningOs();
  }

  private load(): void {
    this.loading = true;
    forkJoin({
      products: this.productService.getProducts(this.softwareRepositoryName, null),
      external: this.softwareService.listSoftwares(this.softwareRepositoryName, false, true),
    })
      .pipe(finalize(() => (this.loading = false)))
      .subscribe((r) => {
        this.products = new Map();
        r.products.forEach((prod) => {
          this.products.set(prod.key.name, this.products.get(prod.key.name) || []);
          this.products.get(prod.key.name).push(prod);
        });

        this.externalPackageGroups = this.buildExternalPackagesMap(r.external);

        if (!this.isProductSelected() && !this.isExternalPackageSelected()) {
          this.selection = null;
        }
        if (this.selection === null) {
          this.sidenav.close();
        }
      });
  }

  private buildExternalPackagesMap(keys: ManifestKey[]): Map<string, SoftwarePackageGroup> {
    const map: Map<string, SoftwarePackageGroup> = new Map();
    keys.forEach((key) => {
      const groupName: string = this.getPackageName(key);
      let group: SoftwarePackageGroup = map.get(groupName);
      if (!group) {
        group = new SoftwarePackageGroup(groupName);
        map.set(groupName, group);
      }
      const packageOs: OperatingSystem = this.getPackageOs(key);
      const supportedOs: OperatingSystem[] = packageOs ? [packageOs] : this.getAllOs();
      for (const os of supportedOs) {
        group.osVersions.set(os, group.osVersions.get(os) || []);
        group.osVersions.get(os).push(key);
      }
    });
    return map;
  }

  private getPackageName(key: ManifestKey) {
    const os = this.getPackageOs(key);
    return os ? key.name.substring(0, key.name.lastIndexOf('/')) : key.name;
  }

  private getPackageOs(key: ManifestKey): OperatingSystem {
    const idx = key.name.lastIndexOf('/');
    return this.string2os(idx >= 0 ? key.name.substring(idx + 1).toUpperCase() : '');
  }

  private string2os(s: string): OperatingSystem {
    try {
      return OperatingSystem[s];
    } catch (e) {
      return undefined;
    }
  }

  getAllOs(): OperatingSystem[] {
    return Object.keys(OperatingSystem)
      .filter((x) => x !== OperatingSystem.UNKNOWN && x !== OperatingSystem.AIX && x !== OperatingSystem.MACOS)
      .map((k) => OperatingSystem[k]);
  }

  switchOs(os: OperatingSystem) {
    this.activeOs = os;
  }

  public versionDeleted(): void {
    this.load();
  }

  public get selectedProductVersions() {
    return this.selection ? this.products.get(this.selection) : null;
  }

  public get selectedProductLatestVersion() {
    return this.selectedProductVersions ? this.selectedProductVersions[0] : null;
  }

  public getSelectedExternalPackageGroup(): SoftwarePackageGroup {
    return this.externalPackageGroups.get(this.selection);
  }

  public select(selection: string): void {
    this.selection = selection;
    this.sidenav.open();
  }

  public isProductSelected(): boolean {
    return this.selectedProductVersions != null;
  }

  public isExternalPackageSelected(): boolean {
    return this.getSelectedExternalPackageGroup() != null;
  }

  public openUploadDialog() {
    const config = new MatDialogConfig();
    config.width = '70%';
    config.height = '80%';
    config.minWidth = '650px';
    config.minHeight = '550px';
    config.data = this.softwareRepositoryName;
    this.dialog
      .open(SoftwareRepoFileUploadComponent, config)
      .afterClosed()
      .subscribe((e) => this.load());
  }

  public isReadOnly(): boolean {
    return !this.authService.isScopedWrite(this.softwareRepositoryName);
  }
}
