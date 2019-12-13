import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { cloneDeep, isEqual } from 'lodash';
import { SettingsConfiguration } from 'src/app/models/gen.dtos';
import { ConfigService } from './config.service';

@Injectable()
export class SettingsService {

  private loading = true;
  private settings: SettingsConfiguration;
  private origSettings: SettingsConfiguration;

  constructor(private config: ConfigService, private http: HttpClient) {
    this.load();
  }

  private load() {
    this.loading = true;
    this.http.get<SettingsConfiguration>(this.config.config.api + '/master/settings').subscribe(r => {
      this.settings = r;
      this.origSettings = cloneDeep(this.settings);
      this.loading = false;
    });
  }

  public isDirty(): boolean {
    return !isEqual(this.settings, this.origSettings);
  }

  public isLoading(): boolean {
    return this.loading;
  }

  public getSettings(): SettingsConfiguration {
    return this.settings;
  }

  public revert() {
    this.load();
  }

  public save() {
    this.loading = true;
    this.http.post<SettingsConfiguration>(this.config.config.api + '/master/settings', this.settings).subscribe(_ => {
      this.load();
    });
  }
}
