import { Component, inject } from '@angular/core';
import { ProductsService } from 'src/app/modules/primary/products/services/products.service';
import { BdDialogComponent } from '../../../../core/components/bd-dialog/bd-dialog.component';
import { BdDialogToolbarComponent } from '../../../../core/components/bd-dialog-toolbar/bd-dialog-toolbar.component';
import { BdDialogContentComponent } from '../../../../core/components/bd-dialog-content/bd-dialog-content.component';
import { BdFileDropComponent } from '../../../../core/components/bd-file-drop/bd-file-drop.component';
import { BdFileUploadComponent } from '../../../../core/components/bd-file-upload/bd-file-upload.component';
import { AsyncPipe } from '@angular/common';

@Component({
    selector: 'app-product-upload',
    templateUrl: './product-upload.component.html',
    imports: [BdDialogComponent, BdDialogToolbarComponent, BdDialogContentComponent, BdFileDropComponent, BdFileUploadComponent, AsyncPipe]
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
