import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { finalize, map } from 'rxjs/operators';
import { ManifestKey, PluginInfoDto } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { DownloadService } from 'src/app/modules/core/services/download.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { RepositoryService } from 'src/app/modules/primary/repositories/services/repository.service';
import { LabelRecord } from '../../products/services/product-details.service';

/**
 * A service which extracts a single product denoted by route parameters. This requires the active route to have
 * a 'key' and a 'tag' route parameter. The service will listen to product data from the products service.
 * <p>
 * This service is *not* provided in root. Any component providing this service *must* have the 'key' and 'tag'
 * route parameters.
 */
@Injectable()
export class SoftwareDetailsService implements OnDestroy {
  public manifestKey$ = new BehaviorSubject<string>(null);
  public manifestTag$ = new BehaviorSubject<string>(null);
  public softwarePackage$ = new BehaviorSubject<any>(null);
  public labels$ = new BehaviorSubject<LabelRecord[]>(null);

  private plugins$ = new BehaviorSubject<PluginInfoDto[]>(null);
  public pluginsLoading$ = new BehaviorSubject<boolean>(false);

  private subscription: Subscription;
  private softwareSubscription: Subscription;

  private productApiPath = () =>
    `${this.cfg.config.api}/softwarerepository/${this.areas.repositoryContext$.value}/product/${this.manifestKey$.value}/${this.manifestTag$.value}`;
  private softwareApiPath = () =>
    `${this.cfg.config.api}/softwarerepository/${this.areas.repositoryContext$.value}/content/${this.manifestKey$.value}/${this.manifestTag$.value}`;
  private pluginApiPath = () =>
    `${this.cfg.config.api}/plugin-admin/list-product-plugins/${this.areas.repositoryContext$.value}`;

  constructor(
    private route: ActivatedRoute,
    private repository: RepositoryService,
    private areas: NavAreasService,
    private cfg: ConfigService,
    private http: HttpClient,
    private downloads: DownloadService
  ) {
    this.subscription = this.route.paramMap.subscribe((p) => {
      this.manifestKey$.next(p.get('key'));
      this.manifestTag$.next(p.get('tag'));

      if (this.softwareSubscription) {
        this.softwareSubscription.unsubscribe();
      }
      this.softwareSubscription = this.repository.data$
        .pipe(
          map((data) =>
            data.find(
              (e) =>
                e.key.name === this.manifestKey$.value &&
                e.key.tag === this.manifestTag$.value
            )
          )
        )
        .subscribe((data) => {
          this.softwarePackage$.next(data);
          this.labels$.next(this.mapLabels(data));
        });
    });
  }

  ngOnDestroy() {
    this.subscription.unsubscribe();
  }

  public getPlugins(): Observable<PluginInfoDto[]> {
    if (!this.plugins$.value && !this.pluginsLoading$.value) {
      this.pluginsLoading$.next(true);
      const key: ManifestKey = {
        name: this.manifestKey$.value,
        tag: this.manifestTag$.value,
      };
      this.http
        .post<PluginInfoDto[]>(this.pluginApiPath(), key)
        .pipe(finalize(() => this.pluginsLoading$.next(false)))
        .subscribe((p) => this.plugins$.next(p));
    }
    return this.plugins$;
  }

  public delete(): Observable<any> {
    return this.http.delete(this.getApiPath4Type());
  }

  public download(original: boolean): Observable<any> {
    const params = new HttpParams().set('original', original);
    return new Observable<any>((s) => {
      this.http
        .get(`${this.getApiPath4Type()}/zip`, {
          params,
          responseType: 'text',
        })
        .subscribe((token) => {
          this.downloads.download(this.downloads.createDownloadUrl(token));
          s.next(token);
          s.complete();
        });
    });
  }

  private getApiPath4Type() {
    if (this.softwarePackage$.value?.type === 'Product') {
      return this.productApiPath();
    } else if (this.softwarePackage$.value?.type === 'External Software') {
      return this.softwareApiPath();
    }
  }

  mapLabels(software: any) {
    const labels: LabelRecord[] = [];
    if (software?.labels) {
      for (const k of Object.keys(software.labels)) {
        labels.push({ key: k, value: software.labels[k] });
      }
    }
    return labels;
  }
}
