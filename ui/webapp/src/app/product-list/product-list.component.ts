import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { from } from 'rxjs';
import { finalize, flatMap, tap } from 'rxjs/operators';
import { ManifestKey, ProductDto } from '../models/gen.dtos';
import { LoggingService } from '../services/logging.service';
import { ProductService } from '../services/product.service';
import { sortByTags } from '../utils/manifest.utils';

@Component({
  selector: 'app-product-list',
  templateUrl: './product-list.component.html',
  styleUrls: ['./product-list.component.css'],
})
export class ProductListComponent implements OnInit {
  private readonly log = this.loggingService.getLogger('ProductListComponent');

  @Input() public instanceGroup: string;
  @Output() public deleted = new EventEmitter();

  private _products: ProductDto[];
  private usageCounts: Map<ManifestKey, number> = new Map();
  public exporting: ProductDto;

  constructor(private productService: ProductService, private loggingService: LoggingService) {}

  public get products(): ProductDto[] {
    return this._products;
  }

  @Input() public set products(products: ProductDto[]) {
    this._products = products;
    if (this._products) {
      sortByTags(this.products, p => p.key.tag, false);
    }
    this.usageCounts = new Map();
    if (this._products) {
      from(this._products)
        .pipe(
          flatMap(p => {
            return this.productService.getProductVersionUsageCount(this.instanceGroup, p.key).pipe(
              tap(e => {
                this.usageCounts.set(p.key, parseInt(e, 10));
              }),
            );
          }, 2),
        )
        .subscribe();
    }
  }

  ngOnInit() {}

  delete(product: ProductDto): void {
    this.productService.deleteProductVersion(this.instanceGroup, product.key).subscribe(r => {
      this.log.message('Successfully deleted ' + product.key.name + ':' + product.key.tag);
      this.deleted.emit();
    });
  }

  deleteTooltip(product: ProductDto): string {
    if (this.usageCounts.get(product.key) === undefined) {
      return 'Calculating...';
    }
    return (
      'Delete product version' +
      (this.usageCount(product) ? ' (used in ' + this.usageCount(product) + ' instances)' : '')
    );
  }

  export(product: ProductDto): void {
    this.exporting = product;
    this.productService
      .createProductZip(this.instanceGroup, product.key)
      .pipe(finalize(() => (this.exporting = null)))
      .subscribe(token => {
        window.location.href = this.productService.downloadProduct(this.instanceGroup, token);
      });
  }

  usageCount(product: ProductDto): number {
    if (this.usageCounts.get(product.key) === undefined) {
      return 1;
    }
    return this.usageCounts.get(product.key);
  }
}
