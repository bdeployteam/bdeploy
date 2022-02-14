import { Component } from '@angular/core';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';

@Component({
  selector: 'app-product-upload',
  templateUrl: './product-upload.component.html',
})
export class ProductUploadComponent {
  /* template */ files: File[] = [];

  constructor(public products: ProductsService) {}

  /* template */ fileAdded(file: File) {
    this.files.push(file);
  }

  /* template */ onDismiss(index: number) {
    this.files.splice(index, 1);
  }
}
