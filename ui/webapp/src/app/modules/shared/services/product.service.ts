import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { InstanceUsageDto, ManifestKey, ProductDto } from '../../../models/gen.dtos';
import { ProcessConfigDto } from '../../../models/process.model';
import { ConfigService } from '../../core/services/config.service';
import { Logger, LoggingService } from '../../core/services/logging.service';
import { InstanceGroupService } from '../../instance-group/services/instance-group.service';
import { DownloadService } from './download.service';

@Injectable({
  providedIn: 'root',
})
export class ProductService {
  private log: Logger = this.loggingService.getLogger('ProductService');

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    private loggingService: LoggingService,
    private downloadService: DownloadService) {}

  public getProducts(instanceGroupName: string, productName: string): Observable<ProductDto[]> {
    const url: string = this.buildProductUrl(instanceGroupName) + '/list';
    this.log.debug('getProducts: ' + url);
    return this.http.get<ProductDto[]>(url, { params: { name: productName ? productName : '' } });
  }

  public getProductCount(instanceGroupName: string): Observable<number> {
    const url: string = this.buildProductUrl(instanceGroupName) + '/count';
    this.log.debug('getProductCount: ' + url);
    return this.http.get<number>(url);
  }

  public deleteProductVersion(instanceGroupName: string, key: ManifestKey) {
    const url: string = this.buildProductNameTagUrl(instanceGroupName, key);
    this.log.debug('deleteProductVersion: ' + url);
    return this.http.delete(url);
  }

  public getProductVersionUsageCount(instanceGroupName: string, key: ManifestKey) {
    const url = this.buildProductNameTagUrl(instanceGroupName, key) + '/useCount';
    this.log.debug('getProductVersionUsageCount: ' + url);
    return this.http.get(url, { responseType: 'text' });
  }

  public getProductVersionUsedIn(instanceGroupName: string, key: ManifestKey): Observable<InstanceUsageDto[]> {
    const url = this.buildProductNameTagUrl(instanceGroupName, key) + '/usedIn';
    this.log.debug('getProductVersionUsedIn: ' + url);
    return this.http.get<InstanceUsageDto[]>(url);
  }

  public getProductDiskUsage(instanceGroupName: string, key: ManifestKey): Observable<string> {
    const url = this.buildProductNameUrl(instanceGroupName, key) + '/diskUsage';
    this.log.debug('getProductDiskUsage: ' + url);
    return this.http.get(url, { responseType: 'text' });
  }

  public createProductZip(instanceGroupName: string, key: ManifestKey): Observable<string> {
    const url = this.buildProductNameTagUrl(instanceGroupName, key) + '/zip';
    this.log.debug('createProductZip: ' + url);
    return this.http.get(url, { responseType: 'text' });
  }

  public downloadProduct(token: string): string {
    return this.downloadService.createDownloadUrl(token);
  }

  public copyProduct(instanceGroupName: string, softwareRepositoryName: string, key: ManifestKey) {
    const url = this.buildProductUrl(instanceGroupName) + '/copy';
    this.log.debug('copyProduct: ' + url);
    const params = new HttpParams().set('repo', softwareRepositoryName).set('name', key.name).set('tag', key.tag);
    return this.http.get(url, { params: params });
  }

  public getProductUploadUrl(instanceGroupName: string): string {
    return this.buildProductUrl(instanceGroupName) + '/upload';
  }

  private buildProductUrl(instanceGroupName: string): string {
    return this.cfg.config.api + InstanceGroupService.BASEPATH + '/' + instanceGroupName + '/product';
  }

  private buildProductNameUrl(instanceGroupName: string, key: ManifestKey): string {
    return this.buildProductUrl(instanceGroupName) + '/' + key.name;
  }

  private buildProductNameTagUrl(instanceGroupName: string, key: ManifestKey): string {
    return this.buildProductUrl(instanceGroupName) + '/' + key.name + '/' + key.tag;
  }

  /**
   * Changes the product version used in the given process configuration.
   * Note that the contained applications must be updated separately.
   *
   * @param config the current configuration
   * @param product the new product version.
   */
  updateProduct(config: ProcessConfigDto, product: ProductDto) {
    config.instance.product = product.key;
    config.version.product = product.key;

    // Update configuration files
    if (!config.instance.configTree) {
      config.instance.configTree = product.configTree;
    }
  }
}
