import { Location } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { forkJoin } from 'rxjs';
import { SORT_PURPOSE } from '../models/consts';
import { DataList } from '../models/dataList';
import { InstanceConfiguration, InstancePurpose, ProductDto } from '../models/gen.dtos';
import { InstanceService } from '../services/instance.service';
import { Logger, LoggingService } from '../services/logging.service';
import { ProductService } from '../services/product.service';

@Component({
  selector: 'app-instance-browser',
  templateUrl: './instance-browser.component.html',
  styleUrls: ['./instance-browser.component.css'],
})
export class InstanceBrowserComponent implements OnInit {
  private readonly log: Logger = this.loggingService.getLogger('InstanceBrowserComponent');

  instanceGroupName: string = this.route.snapshot.paramMap.get('name');

  loading = true;
  hasProducts = false;
  products: ProductDto[];
  instanceList: DataList<InstanceConfiguration> = new DataList();
  purposes: InstancePurpose[] = [];

  constructor(
    private route: ActivatedRoute,
    private instanceService: InstanceService,
    private productService: ProductService,
    private loggingService: LoggingService,
    public location: Location,
  ) {}

  ngOnInit(): void {
    this.instanceList.searchCallback = (instance: InstanceConfiguration, text: string) => {
      if (instance.name.toLowerCase().includes(text)) {
        return true;
      }
      if (instance.description.toLowerCase().includes(text)) {
        return true;
      }
      if (instance.uuid.toLowerCase() === text) {
        return true;
      }
      if (instance.product.name.toLowerCase().includes(text)) {
        return true;
      }
      if (instance.product.tag.toLowerCase().startsWith(text)) {
        return true;
      }
      return false;
    };
    this.loadInstances();
  }

  loadInstances() {
    this.purposes = [];
    this.instanceList.clear();
    this.loading = true;

    const instancePromise = this.instanceService.listInstances(this.instanceGroupName);
    instancePromise.subscribe(instances => {
      const unsortedSet = new Set<InstancePurpose>();
      instances.forEach(instance => unsortedSet.add(instance.purpose));
      this.purposes = Array.from(unsortedSet).sort(SORT_PURPOSE);
      this.instanceList.addAll(instances);
      this.log.debug(`Got ${instances.length} instances grouped into ${this.purposes.length} purposes`);
    });

    const productPromise = this.productService.getProducts(this.instanceGroupName);
    productPromise.subscribe(products => {
      this.hasProducts = products.length > 0;
      this.products = products;
    });

    forkJoin(instancePromise, productPromise).subscribe(result => {
      this.loading = false;
    });
  }

  getProductOfInstance(instance: InstanceConfiguration): ProductDto {
    if (!this.products) {
      return null;
    }
    return this.products.find(p => p.key.name === instance.product.name && p.key.tag === instance.product.tag);
  }

  getInstancesByPurpose(purpose: InstancePurpose): InstanceConfiguration[] {
    const filtered = this.instanceList.filtered.filter(instance => instance.purpose === purpose);
    const sorted = filtered.sort((a, b) => {
      return a.name.localeCompare(b.name);
    });
    return sorted;
  }

  remove(instance: InstanceConfiguration) {
    this.loadInstances();
  }
}
