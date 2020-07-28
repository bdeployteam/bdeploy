import { Location } from '@angular/common';
import { Component, OnInit, ViewChild } from '@angular/core';
import { MatDialog, MatDialogConfig } from '@angular/material/dialog';
import { MatDrawer } from '@angular/material/sidenav';
import { ActivatedRoute } from '@angular/router';
import { finalize } from 'rxjs/operators';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { MinionMode, ProductDto } from '../../../../models/gen.dtos';
import { ConfigService } from '../../../core/services/config.service';
import { FileUploadComponent } from '../../../shared/components/file-upload/file-upload.component';
import { sortByTags } from '../../../shared/utils/manifest.utils';
import { ProductService } from '../../services/product.service';

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

  private grid = new Map([['xs', 1], ['sm', 1], ['md', 2], ['lg', 3], ['xl', 5]]);

  loading = false;

  constructor(
    public authService: AuthenticationService,
    private productService: ProductService,
    private route: ActivatedRoute,
    public dialog: MatDialog,
    public location: Location,
    private config: ConfigService,
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

  private loadProducts() {
    this.loading = true;

    const productPromise = this.productService.getProducts(this.instanceGroup, null);
    productPromise.pipe(finalize(() => this.loading = false)).subscribe(p => {
      this.products = new Map();
      p.forEach(prod => {
        this.products.set(prod.key.name, this.products.get(prod.key.name) || []);
        this.products.get(prod.key.name).push(prod);
      });
      this.productsKeys = Array.from(this.products.keys());
      this.productsKeys.forEach(key => {
        const versions: ProductDto[] = this.products.get(key);
          this.products.set(key, sortByTags(versions, v => v.key.tag, false));
      });
      if (this.selectedProductKey && this.productsKeys.indexOf(this.selectedProductKey) === -1) {
        this.selectedProductKey = null;
      }
      if (!this.selectedProductKey) {
        this.sidenav.close();
      }
    });
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
      headerMessage: 'Upload products into this instance group. The selected archive may either contain a new product or a new version of an existing product.',
      url: this.productService.getProductUploadUrl(this.instanceGroup),
      fileTypes: ['.zip']
    };
    this.dialog
      .open(FileUploadComponent, config)
      .afterClosed()
      .subscribe(e => {
        this.loadProducts();
      });
  }
}
