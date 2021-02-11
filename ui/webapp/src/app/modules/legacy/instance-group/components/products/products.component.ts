import { Location } from '@angular/common';
import { Component, OnInit, ViewChild } from '@angular/core';
import { MatDialog, MatDialogConfig } from '@angular/material/dialog';
import { MatDrawer } from '@angular/material/sidenav';
import { ActivatedRoute } from '@angular/router';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { RoutingHistoryService } from 'src/app/modules/legacy/core/services/routing-history.service';
import { MinionMode, ProductDto } from '../../../../../models/gen.dtos';
import { ConfigService } from '../../../../core/services/config.service';
import { FileUploadComponent } from '../../../../legacy/shared/components/file-upload/file-upload.component';
import { ProductService } from '../../../../legacy/shared/services/product.service';
import { ProductsCopyComponent } from '../products-copy/products-copy.component';

@Component({
  selector: 'app-products',
  templateUrl: './products.component.html',
  styleUrls: ['./products.component.css'],
})
export class ProductsComponent implements OnInit {
  @ViewChild('appsidenav', { static: true })
  sidenav: MatDrawer;

  public instanceGroup: string;
  public products: Map<string, ProductDto[]> = new Map();
  public selectedProductKey: string = null;
  public productsKeys: string[];

  private grid = new Map([
    ['xs', 1],
    ['sm', 1],
    ['md', 2],
    ['lg', 3],
    ['xl', 5],
  ]);

  loading = false;

  constructor(
    public authService: AuthenticationService,
    private productService: ProductService,
    private route: ActivatedRoute,
    public dialog: MatDialog,
    public location: Location,
    private config: ConfigService,
    public routingHistoryService: RoutingHistoryService
  ) {}

  ngOnInit() {
    this.instanceGroup = this.route.snapshot.paramMap.get('group');
    this.loadProducts();
  }

  isCentral() {
    return this.config.config.mode === MinionMode.CENTRAL;
  }

  public get selectedProductVersions() {
    return this.selectedProductKey ? this.products.get(this.selectedProductKey) : null;
  }

  public get selectedProductLatestVersion() {
    const versions = this.selectedProductVersions;
    return versions ? versions[0] : null;
  }

  private async loadProducts() {
    this.loading = true;

    try {
      const p = await this.productService.getProducts(this.instanceGroup, null).toPromise();

      this.products = new Map();
      p.forEach((prod) => {
        this.products.set(prod.key.name, this.products.get(prod.key.name) || []);
        this.products.get(prod.key.name).push(prod);
      });

      this.productsKeys = Array.from(this.products.keys());
      if (this.selectedProductKey && this.productsKeys.indexOf(this.selectedProductKey) === -1) {
        this.selectedProductKey = null;
      }
      if (!this.selectedProductKey) {
        this.sidenav.close();
      }
    } finally {
      this.loading = false;
    }
  }

  versionDeleted(): void {
    this.loadProducts();
  }

  openProduct(productKey: string): void {
    this.selectedProductKey = productKey;
    this.sidenav.open();
  }

  openUploadDialog() {
    const config = new MatDialogConfig();
    config.width = '70%';
    config.height = '75%';
    config.minWidth = '650px';
    config.minHeight = '550px';
    config.data = {
      title: 'Upload Products',
      headerMessage:
        'Upload products into this instance group. The selected archive may either contain a new product or a new version of an existing product.',
      url: this.productService.getProductUploadUrl(this.instanceGroup),
      fileTypes: ['.zip'],
    };
    this.dialog
      .open(FileUploadComponent, config)
      .afterClosed()
      .subscribe((e) => {
        this.loadProducts();
      });
  }

  openCopyDialog() {
    const config = new MatDialogConfig();
    config.width = '70%';
    config.height = '75%';
    config.minWidth = '650px';
    config.minHeight = '550px';
    config.data = {
      instanceGroup: this.instanceGroup,
      existingProducts: this.products,
    };
    this.dialog
      .open(ProductsCopyComponent, config)
      .afterClosed()
      .subscribe((e) => {
        this.loadProducts();
      });
  }
}
