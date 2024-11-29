import { Component, inject } from '@angular/core';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';

@Component({
    selector: 'app-product-upload',
    templateUrl: './product-upload.component.html',
    standalone: false
})
export class ProductUploadComponent {
  protected readonly products = inject(ProductsService);

  protected files: File[] = [];

  protected fileAdded(file: File) {
    this.files.push(file);
  }

  protected onDismiss(index: number) {
    this.files.splice(index, 1);
  }
}
