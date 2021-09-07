import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { CleanupGroup } from '../../../../models/gen.dtos';
import { ConfigService } from '../../../core/services/config.service';
import { Logger, LoggingService } from '../../../core/services/logging.service';

@Injectable({
  providedIn: 'root',
})
export class CleanupService {
  private static BASEPATH = '/cleanUi';
  private log: Logger = this.loggingService.getLogger('CleanupService');

  constructor(private cfg: ConfigService, private http: HttpClient, private loggingService: LoggingService) {}

  public calculateCleanup(): Observable<CleanupGroup[]> {
    const url: string = this.cfg.config.api + CleanupService.BASEPATH;
    this.log.debug('calculateCleanup: ' + url);
    return this.http.get<CleanupGroup[]>(url);
  }

  public performCleanup(groups: CleanupGroup[]) {
    const url: string = this.cfg.config.api + CleanupService.BASEPATH;
    this.log.debug('performCleanup: ' + url);
    return this.http.post(url, groups);
  }
}
