import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { from } from 'rxjs';
import { finalize, flatMap, tap } from 'rxjs/operators';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { ManifestKey, ProductDto } from '../../../../models/gen.dtos';
import { LoggingService } from '../../../core/services/logging.service';
import { DownloadService } from '../../../shared/services/download.service';
import { ProductService } from '../../services/product.service';

@Component({
  selector: 'app-product-list',
  templateUrl: './product-list.component.html',
  styleUrls: ['./product-list.component.css'],
})
export class ProductListComponent implements OnChanges {
  private readonly log = this.loggingService.getLogger('ProductListComponent');

  @Input() public instanceGroup: string;
  @Input() public products: ProductDto[];

  @Input() public showUsedIn: boolean = true;
  @Input() public showDownload: boolean = true;
  @Input() public showDelete: boolean = true;

  @Input() public selectable: boolean = false;

  @Output() public deleted = new EventEmitter();
  @Output() public selected = new EventEmitter();

  public selection: ProductDto;
  private usageCounts: Map<ManifestKey, number> = new Map();
  public exporting: ProductDto;

  constructor(
    private productService: ProductService,
    private loggingService: LoggingService,
    public authService: AuthenticationService,
    private downloadService: DownloadService) {}

  ngOnChanges() {
    if (this.instanceGroup && this.products && this.products.length > 0) {
      this.usageCounts = new Map();
      this.selection = undefined;
      from(this.products).pipe(flatMap(p => {
          return this.productService.getProductVersionUsageCount(this.instanceGroup, p.key).pipe(tap(e => {this.usageCounts.set(p.key, parseInt(e, 10));}));
        }, 2),
      ).subscribe();
    }
  }

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
      (this.usageCount(product) ? ' (used in ' + this.usageCount(product) + ' instance versions)' : '')
    );
  }

  select(selection: ProductDto): void {
    this.selection = selection;
    this.selected.emit(selection);
  }

  export(product: ProductDto): void {
    this.exporting = product;
    this.productService
      .createProductZip(this.instanceGroup, product.key)
      .pipe(finalize(() => (this.exporting = null)))
      .subscribe(token => {
        this.downloadService.download(this.productService.downloadProduct(token));
      });
  }

  usageCount(product: ProductDto): number {
    if (this.usageCounts.get(product.key) === undefined) {
      return 1;
    }
    return this.usageCounts.get(product.key);
  }

  getItemClass(product: ProductDto) {
    if (!this.selectable) {
      return undefined;
    }
    return product === this.selection ? 'prod-row-selected' : 'prod-row-unselected';
  }
}
