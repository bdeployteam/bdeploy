import { Location } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { forkJoin } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { AuthenticationService } from 'src/app/modules/core/services/authentication.service';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { RoutingHistoryService } from 'src/app/modules/core/services/routing-history.service';
import { InstanceGroupService } from 'src/app/modules/instance-group/services/instance-group.service';
import { SORT_PURPOSE } from '../../../../models/consts';
import { DataList } from '../../../../models/dataList';
import { CustomAttributesRecord, InstanceDto, InstanceGroupConfiguration, InstancePurpose, MinionMode } from '../../../../models/gen.dtos';
import { Logger, LoggingService } from '../../../core/services/logging.service';
import { ProductService } from '../../../instance-group/services/product.service';
import { InstanceService } from '../../services/instance.service';

@Component({
  selector: 'app-instance-browser',
  templateUrl: './instance-browser.component.html',
  styleUrls: ['./instance-browser.component.css'],
})
export class InstanceBrowserComponent implements OnInit {
  private readonly log: Logger = this.loggingService.getLogger('InstanceBrowserComponent');

  instanceGroupName: string = this.route.snapshot.paramMap.get('name');

  loading = true;
  instanceGroup: InstanceGroupConfiguration;
  hasProducts = false;
  instanceDtoList: DataList<InstanceDto> = new DataList();
  instancesAttributes: { [index: string]: CustomAttributesRecord } = {};
  purposes: InstancePurpose[] = [];

  groupAttribute: string;
  groupAttributeValuesSelected: string[];

  constructor(
    public authService: AuthenticationService,
    private route: ActivatedRoute,
    private instanceGroupService: InstanceGroupService,
    private instanceService: InstanceService,
    private productService: ProductService,
    private loggingService: LoggingService,
    public location: Location,
    private config: ConfigService,
    public routingHistoryService: RoutingHistoryService
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
      const attributes: {[index: string]: string } = this.instancesAttributes[instanceDto.instanceConfiguration.uuid].attributes;
      if (attributes && Object.keys(attributes).find(a => attributes[a] && attributes[a].toLowerCase().includes(text))) {
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

    forkJoin({
      instanceGroup: this.instanceGroupService.getInstanceGroup(this.instanceGroupName),
      instances: this.instanceService.listInstances(this.instanceGroupName),
      instancesAttributes: this.instanceService.listInstancesAttributes(this.instanceGroupName),
      productCount: this.productService.getProductCount(this.instanceGroupName),
    }).pipe(finalize(() => this.loading = false))
      .subscribe(r => {
        this.instanceGroup = r.instanceGroup;
        this.instanceDtoList.addAll(r.instances);
        this.purposes = Array.from(new Set(r.instances.map(i => i.instanceConfiguration.purpose))).sort(SORT_PURPOSE);
        this.instancesAttributes = r.instancesAttributes;
        this.hasProducts = r.productCount > 0;
      })
  }

  getPurposes(attributeValue: string) {
    return Array.from(new Set(this.instanceDtoList.filtered
      .filter(dto => this.instancesAttributes[dto.instanceConfiguration.uuid]?.attributes?.[this.groupAttribute] == attributeValue)
      .map(dto => dto.instanceConfiguration.purpose)
      .sort(SORT_PURPOSE)));
  }

  getInstanceDtos(attributeValue: string, purpose: InstancePurpose): InstanceDto[] {
    return this.instanceDtoList.filtered.filter(dto => dto.instanceConfiguration.purpose === purpose
      && (!this.groupAttribute || this.instancesAttributes[dto.instanceConfiguration.uuid]?.attributes[this.groupAttribute] == attributeValue))
      .sort((a,b) => a.instanceConfiguration.name.localeCompare(b.instanceConfiguration.name));
  }

  remove() {
    this.loadInstances();
  }

  isCentral() {
    return this.config.config.mode === MinionMode.CENTRAL;
  }

  isManaged() {
    return this.config.config.mode === MinionMode.MANAGED;
  }
}
