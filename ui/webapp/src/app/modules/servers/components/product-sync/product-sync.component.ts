import { Location } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { MatCheckboxChange, MatRadioChange, MatTableDataSource } from '@angular/material';
import { ActivatedRoute } from '@angular/router';
import { forkJoin } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { ManagedMasterDto, MinionMode, ProductDto, ProductTransferDto } from '../../../../models/gen.dtos';
import { ProductService } from '../../../../services/product.service';
import { ManagedServersService } from '../../services/managed-servers.service';

@Component({
  selector: 'app-product-sync',
  templateUrl: './product-sync.component.html',
  styleUrls: ['./product-sync.component.css']
})
export class ProductSyncComponent implements OnInit {

  public instanceGroup: string;
  public sourceType = MinionMode.CENTRAL;
  public sourceManagedServer: ManagedMasterDto;

  public targetType = MinionMode.MANAGED;
  public targetManagedServer: ManagedMasterDto;

  loading = true;
  loadingProducts = true;
  startingTransfer = true;
  managedServers: ManagedMasterDto[];

  columnsToDisplay = ['status', 'name', 'version', 'info'];
  dataSource: MatTableDataSource<ProductDto>;
  targetProducts: ProductDto[];
  processingProducts: ProductDto[];

  selectedProducts: ProductDto[] = [];

  constructor(private route: ActivatedRoute, public location: Location, private servers: ManagedServersService, private productService: ProductService) { }

  ngOnInit() {
    this.instanceGroup = this.route.snapshot.paramMap.get('group');

    this.servers.getManagedServers(this.instanceGroup).pipe(finalize(() => this.loading = false)).subscribe(r => {
      this.managedServers = r.sort((a, b) => a.name.localeCompare(b.name));
    });
  }

  updateSource($event: MatRadioChange) {
    this.sourceType = $event.value;
    this.sourceManagedServer = null;
    this.targetManagedServer = null;
  }

  updateTarget($event: MatRadioChange) {
    this.targetType = $event.value;
    this.targetManagedServer = null;
  }

  isSourceOK(): boolean {
    if (this.sourceType === MinionMode.MANAGED && !this.sourceManagedServer) {
      return false;
    }
    return true;
  }

  isTargetOK(): boolean {
    if (this.targetType === MinionMode.MANAGED && !this.targetManagedServer) {
      return false;
    }
    return true;
  }

  loadProducts() {
    // load products from source server.
    let call0 = this.productService.getProducts(this.instanceGroup);
    if (this.sourceType !== MinionMode.CENTRAL) {
      call0 = this.servers.productsOfManagedServer(this.instanceGroup, this.sourceManagedServer.name);
    }

    // load products from the target server and check/disable entries of products already there.
    let call1 = this.productService.getProducts(this.instanceGroup);
    if (this.targetType !== MinionMode.CENTRAL) {
      call1 = this.servers.productsOfManagedServer(this.instanceGroup, this.targetManagedServer.name);
    }

    const call2 = this.servers.productsInTransfer(this.instanceGroup);

    // execute and wait for all of the calls.
    forkJoin([call0, call1, call2]).pipe(finalize(() => this.loadingProducts = false)).subscribe(results => {
      this.dataSource = new MatTableDataSource(results[0]);
      this.targetProducts = results[1];
      this.processingProducts = results[2];
    });
  }

  isProcessing(prod: ProductDto): boolean {
    if (this.processingProducts && this.containsProd(this.processingProducts, prod)) {
      return true;
    }
    return false;
  }

  isAlreadyPresent(prod: ProductDto): boolean {
    if (this.targetProducts && this.containsProd(this.targetProducts, prod)) {
      return true;
    }
    return false;
  }

  private containsProd(arr: ProductDto[], prod: ProductDto): boolean {
    if (arr.find(x => x.key.name === prod.key.name && x.key.tag === prod.key.tag)) {
      return true;
    }
    return false;
  }

  clearProducts() {
    this.dataSource = null;
    this.loadingProducts = true;
  }

  toggleRowSelection($event: MatCheckboxChange, row: ProductDto) {
    if ($event.checked) {
      if (this.containsProd(this.selectedProducts, row)) {
        return;
      }
      this.selectedProducts.push(row);
    } else {
      this.selectedProducts.splice(this.selectedProducts.indexOf(row), 1);
    }
  }

  startTransfer() {
    const data: ProductTransferDto = {
      sourceMode: this.sourceType,
      sourceServer: this.sourceType === MinionMode.MANAGED ? this.sourceManagedServer.name : null,
      targetMode: this.targetType,
      targetServer: this.targetType === MinionMode.MANAGED ? this.targetManagedServer.name : null,
      versionsToTransfer: this.selectedProducts
    };

    this.servers.startTransfer(this.instanceGroup, data).subscribe(r => {
      this.startingTransfer = false;
    });
  }

}
