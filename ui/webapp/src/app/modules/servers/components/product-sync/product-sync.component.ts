import { Location } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { isEqual } from 'lodash-es';
import { DragulaService } from 'ng2-dragula';
import { forkJoin, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { ManagedMasterDto, MinionMode, ProductDto, ProductTransferDto } from 'src/app/models/gen.dtos';
import { RoutingHistoryService } from 'src/app/modules/core/services/routing-history.service';
import { ProductService } from 'src/app/modules/shared/services/product.service';
import { ManagedServersService } from '../../services/managed-servers.service';

@Component({
  selector: 'app-product-sync',
  templateUrl: './product-sync.component.html',
  styleUrls: ['./product-sync.component.css']
})
export class ProductSyncComponent implements OnInit, OnDestroy {

  public OPTION_CENTRAL = '$$CENTRAL$$';

  public instanceGroup: string;

  public managedServers: ManagedMasterDto[];

  public sourceSelection = null;
  public targetSelection = null;

  public sourceProducts: Map<string, ProductDto[]> = new Map();
  public sourceProductsKeys: string[];
  private _selectedSourceProductKey: string;
  set selectedSourceProductKey(key) {
    this._selectedSourceProductKey = key;
    this.selectableProducts = this.sourceProducts.get(key).filter(p => !this.isAlreadyPresent(p) && !this.isProcessing(p) && !this.isSelected(p));
  }

  targetProducts: ProductDto[];
  processingProducts: ProductDto[];

  public selectableProducts: ProductDto[] = []; // copy of sourceProducts[selection]
  public selectedProducts: ProductDto[] = [];

  loading = true;
  loadingProducts = true;

  transferStarted = false;
  transferDone = false;
  transferHandle: any;

  private subscription: Subscription = new Subscription();

  constructor(
    private route: ActivatedRoute,
    public location: Location,
    private servers: ManagedServersService,
    private productService: ProductService,
    private dragulaService: DragulaService,
    public routingHistoryService: RoutingHistoryService
  ) {}

  public ngOnInit(): void {
    this.instanceGroup = this.route.snapshot.paramMap.get('group');

    this.servers.getManagedServers(this.instanceGroup).pipe(finalize(() => this.loading = false)).subscribe(r => {
      this.managedServers = r.sort((a, b) => a.hostName.localeCompare(b.hostName));
    });

    this.subscription.add(this.dragulaService.drop('PROD_VERSIONS').subscribe(({name, el, source}) => {
      this.selectedSourceProductKey = this._selectedSourceProductKey; // update selectable product versions
    }));
  }

  public ngOnDestroy(): void {
    this.subscription.unsubscribe();
    this.resetProgress();
  }

  loadProducts() {
    this.resetProgress();

    // load products from source server
    let call0 = this.productService.getProducts(this.instanceGroup, null);
    if (this.getMinionMode(this.sourceSelection) !== MinionMode.CENTRAL) {
      call0 = this.servers.productsOfManagedServer(this.instanceGroup, this.sourceSelection);
    }

    // load products from the target server
    let call1 = this.productService.getProducts(this.instanceGroup, null);
    if (this.getMinionMode(this.targetSelection) !== MinionMode.CENTRAL) {
      call1 = this.servers.productsOfManagedServer(this.instanceGroup, this.targetSelection);
    }

    const call2 = this.servers.productsInTransfer(this.instanceGroup);

    // execute and wait for all of the calls.
    forkJoin([call0, call1, call2]).pipe(finalize(() => this.loadingProducts = false)).subscribe(results => {
      this.sourceProducts = new Map();
      results[0].forEach(prod => {
        this.sourceProducts.set(prod.name, this.sourceProducts.get(prod.name) || []);
        this.sourceProducts.get(prod.name).push(prod);
      });
      this.sourceProductsKeys = Array.from(this.sourceProducts.keys());

      this.targetProducts = results[1];
      this.processingProducts = results[2];
    });
  }

  public isSourceAndTargetOK(): boolean {
    return this.sourceSelection != null && this.targetSelection != null;
  }

  public getSelectionLabel(value: string) {
    if (value === this.OPTION_CENTRAL) {
      return 'Central Server';
    }
    return 'Managed: ' + value;
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

  isSelected(prod: ProductDto): boolean {
    if (this.selectedProducts && this.containsProd(this.selectedProducts, prod)) {
      return true;
    }
    return false;
  }

  public selectProductVersion(productVersion: ProductDto) {
    if (this.selectedProducts.indexOf(productVersion) === -1) {
      this.selectedProducts.push(productVersion);
      this.selectedSourceProductKey = this._selectedSourceProductKey; // update selectable product versions
    }
  }

  public deselectProductVersion(productVersion: ProductDto) {
    this.selectedProducts = this.selectedProducts.filter(p => !isEqual(p.key, productVersion.key));
    this.selectedSourceProductKey = this._selectedSourceProductKey; // update selectable product versions
  }

  clearProducts() {
    this.sourceProducts = new Map();
    this.selectedProducts.length = 0;
    this.loadingProducts = true;
  }

  private containsProd(arr: ProductDto[], prod: ProductDto): boolean {
    if (arr.find(x => x.key.name === prod.key.name && x.key.tag === prod.key.tag)) {
      return true;
    }
    return false;
  }

  private getMinionMode(selection: string) {
    return selection === this.OPTION_CENTRAL ? MinionMode.CENTRAL : MinionMode.MANAGED;
  }

  private isCentralOption(selection: string): boolean {
    return selection === this.OPTION_CENTRAL;
  }

  startTransfer() {
    this.resetProgress();

    const data: ProductTransferDto = {
      sourceMode: this.getMinionMode(this.sourceSelection),
      sourceServer: this.isCentralOption(this.sourceSelection) ? null : this.sourceSelection,
      targetMode: this.getMinionMode(this.targetSelection),
      targetServer: this.isCentralOption(this.targetSelection) ? null : this.targetSelection,
      versionsToTransfer: this.selectedProducts
    };

    this.transferStarted = true;
    this.servers.startTransfer(this.instanceGroup, data).subscribe();

    // Enable timer to execute regular updates
    this.transferHandle = setInterval(() => this.updateTransferState(), 1000);
  }

  async updateTransferState() {
    const inTransfer = await this.servers.productsInTransfer(this.instanceGroup).toPromise();
    this.transferDone = inTransfer.length === 0;
  }

  resetProgress() {
    this.transferDone = false;
    this.transferStarted = false;
    if (this.transferHandle) {
      clearInterval(this.transferHandle);
    }
  }

}
