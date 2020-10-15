import { Component, Inject, OnInit } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { finalize } from 'rxjs/operators';
import { SoftwareRepositoryService } from 'src/app/modules/repositories/services/software-repository.service';
import { ProductService } from 'src/app/modules/shared/services/product.service';
import { ProductDto, SoftwareRepositoryConfiguration } from '../../../../models/gen.dtos';


@Component({
  selector: 'app-products-copy',
  templateUrl: './products-copy.component.html',
  styleUrls: ['./products-copy.component.css']
})
export class ProductsCopyComponent implements OnInit {

  repositories: SoftwareRepositoryConfiguration[] = [];
  selectedRepository: SoftwareRepositoryConfiguration;

  public products: Map<string, ProductDto[]> = new Map();
  get productsKeys(): string[] {return Array.from(this.products.keys())};
  public selectedProductKey: string = null;

  public selectedProductVersion: ProductDto;

  loading = true;
  processing = false;

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: any,
    private dialogRef: MatDialogRef<ProductsCopyComponent>,
    private softwareRepositoryService: SoftwareRepositoryService,
    private productService: ProductService,
  ) { }

  ngOnInit() {
    this.loading = true;
    this.softwareRepositoryService.listSoftwareRepositories().pipe(finalize(() => this.loading = false)).subscribe(repositories => {
      this.repositories = repositories;
    });

  }

  selectionRepository(selection: SoftwareRepositoryConfiguration) {
    if (selection !== this.selectedRepository) {
      this.selectedRepository = selection;
      this.selectedProductKey = undefined;
      this.selectedProductVersion = undefined;
      this.productService.getProducts(this.selectedRepository.name, null).pipe(finalize(()=> this.loading = false)).subscribe(products => {
        this.products = new Map();
        products.forEach(prod => {
          const existingProducts: ProductDto[] = this.data.existingProducts?.get(prod.key.name);
          const existing = existingProducts && existingProducts.find(p => p.key.name === prod.key.name && p.key.tag === prod.key.tag) != undefined;
          if (!existing) {
            this.products.set(prod.key.name, this.products.get(prod.key.name) || []);
            this.products.get(prod.key.name).push(prod);
          }
        });
      });
    }
  }

  selectProduct(productKey: string): void {
    if (!this.processing && productKey !== this.selectedProductKey) {
      this.selectedProductKey = productKey;
      this.selectedProductVersion = undefined;
    }
  }

  selectProductVersion(version: ProductDto) {
    if (!this.processing) {
      this.selectedProductVersion = version;
    }
  }

  public get selectedProductVersions() {
    return this.selectedProductKey ? this.products.get(this.selectedProductKey) : null;
  }

  public get selectedProductLatestVersion() {
    const versions = this.selectedProductVersions;
    return versions ? versions[0] : null;
  }

  onCopyButtonPressed(): void {
    this.processing = true;
    this.productService.copyProduct(this.data.instanceGroup, this.selectedRepository.name, this.selectedProductVersion.key).pipe(finalize(() => this.processing = false)).subscribe(_ => {
      this.dialogRef.close();
    });

  }
}
