import { Component, OnDestroy, OnInit } from '@angular/core';
import { MediaChange, MediaObserver } from '@angular/flex-layout';
import { Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { DataList } from '../../../../../models/dataList';
import { SoftwareRepositoryConfiguration } from '../../../../../models/gen.dtos';
import { AuthenticationService } from '../../../../core/services/authentication.service';
import { Logger, LoggingService } from '../../../../core/services/logging.service';
import { SoftwareRepositoryService } from '../../services/software-repository.service';

@Component({
  selector: 'app-software-repositories-browser',
  templateUrl: './software-repositories-browser.component.html',
  styleUrls: ['./software-repositories-browser.component.css'],
})
export class SoftwareRepositoriesBrowserComponent implements OnInit, OnDestroy {
  log: Logger = this.loggingService.getLogger('SoftwareRepositoriesBrowserComponent');

  private subscription: Subscription;
  loading = true;

  private grid = new Map([
    ['xs', 1],
    ['sm', 1],
    ['md', 2],
    ['lg', 3],
    ['xl', 5],
  ]);
  // calculated number of columns
  columns = 3;

  softwareRepositoryList: DataList<SoftwareRepositoryConfiguration>;

  constructor(
    private mediaObserver: MediaObserver,
    private softwareRepositoryService: SoftwareRepositoryService,
    private loggingService: LoggingService,
    private authService: AuthenticationService
  ) {}

  ngOnInit(): void {
    this.log.debug('intializing...');
    this.softwareRepositoryList = new DataList();
    this.softwareRepositoryList.searchCallback = (repo: SoftwareRepositoryConfiguration, text: string) => {
      if (repo.name.toLowerCase().includes(text)) {
        return true;
      }
      if (repo.description.toLowerCase().includes(text)) {
        return true;
      }
      return false;
    };

    this.subscription = this.mediaObserver.media$.subscribe((change: MediaChange) => {
      this.columns = this.grid.get(change.mqAlias);
    });

    this.loadSoftwareRepositories();
  }

  private loadSoftwareRepositories() {
    this.loading = true;
    this.softwareRepositoryService
      .listSoftwareRepositories()
      .pipe(finalize(() => (this.loading = false)))
      .subscribe((repositories) => {
        this.softwareRepositoryList.addAll(repositories);
        this.log.debug('got ' + repositories.length + ' repositories');
      });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  onRemoveRepo(repository: SoftwareRepositoryConfiguration) {
    this.softwareRepositoryList.remove((c) => c === repository);
  }

  public isReadOnly(): boolean {
    return !this.authService.isGlobalAdmin();
  }
}
