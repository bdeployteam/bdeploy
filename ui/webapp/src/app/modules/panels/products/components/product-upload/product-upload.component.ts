import { Component, OnInit } from '@angular/core';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';

@Component({
  selector: 'app-product-upload',
  templateUrl: './product-upload.component.html',
  styleUrls: ['./product-upload.component.css'],
})
export class ProductUploadComponent implements OnInit {
  /* template */ files: File[] = [];

  constructor(public products: ProductsService) {}

  ngOnInit(): void {}

  fileAdded(file: File) {
    this.files.push(file);
  }

  onDismiss(index: number) {
    this.files.splice(index, 1);
  }
}
