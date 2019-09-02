import { Location } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { forkJoin } from 'rxjs';
import { SORT_PURPOSE } from '../models/consts';
import { DataList } from '../models/dataList';
import { InstanceConfiguration, InstanceDto, InstancePurpose, ProductDto } from '../models/gen.dtos';
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
  instanceDtoList: DataList<InstanceDto> = new DataList();
  purposes: InstancePurpose[] = [];

  constructor(
    private route: ActivatedRoute,
    private instanceService: InstanceService,
    private productService: ProductService,
    private loggingService: LoggingService,
    public location: Location,
  ) {}

  ngOnInit(): void {
    this.instanceDtoList.searchCallback = (instanceDto: InstanceDto, text: string) => {
      if (instanceDto.instanceConfiguration.name.toLowerCase().includes(text)) {
        return true;
      }
      if (instanceDto.instanceConfiguration.description.toLowerCase().includes(text)) {
        return true;
      }
      if (instanceDto.instanceConfiguration.uuid.toLowerCase() === text) {
        return true;
      }
      if (instanceDto.productDto.key.name.toLowerCase().includes(text)) {
        return true;
      }
      if (instanceDto.productDto.key.tag.toLowerCase().startsWith(text)) {
        return true;
      }
      return false;
    };
    this.loadInstances();
  }

  loadInstances() {
    this.purposes = [];
    this.instanceDtoList.clear();
    this.loading = true;

    const instancePromise = this.instanceService.listInstances(this.instanceGroupName);
    instancePromise.subscribe(instanceDtos => {
      const unsortedSet = new Set<InstancePurpose>();
      instanceDtos.forEach(instanceDto => unsortedSet.add(instanceDto.instanceConfiguration.purpose));
      this.purposes = Array.from(unsortedSet).sort(SORT_PURPOSE);
      this.instanceDtoList.addAll(instanceDtos);
      this.log.debug(`Got ${instanceDtos.length} instances grouped into ${this.purposes.length} purposes`);
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

  getInstanceDtosByPurpose(purpose: InstancePurpose): InstanceDto[] {
    const filtered = this.instanceDtoList.filtered.filter(instanceDto => instanceDto.instanceConfiguration.purpose === purpose);
    const sorted = filtered.sort((a, b) => {
      return a.instanceConfiguration.name.localeCompare(b.instanceConfiguration.name);
    });
    return sorted;
  }

  remove(instance: InstanceConfiguration) {
    this.loadInstances();
  }
}
