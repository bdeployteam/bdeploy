import { Component, OnInit } from '@angular/core';
import { RouteConfigLoadEnd, RouteConfigLoadStart, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { Logger, LoggingService } from './modules/core/services/logging.service';
import { NavAreasService } from './modules/core/services/nav-areas.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css'],
})
export class AppComponent implements OnInit {
  log: Logger = this.loggingService.getLogger('AppComponent');

  title = 'BDeploy';

  subscription: Subscription;
  loadCount = 0;

  constructor(private loggingService: LoggingService, private router: Router, private areas: NavAreasService) {
    this.log.info('----------------------------------------');
    this.log.info(this.title + ' started...');
    this.log.info('----------------------------------------');
  }

  ngOnInit() {
    this.subscription = this.areas.primaryRoute$.subscribe((url) => {
      if (!url) {
        return;
      }
      // safety, reduce load count, route is loaded so it cannot be loading anymore :)
      // this can happen if lazy loading is initiated but a guard redirects the router
      // somewhere else.
      this.decreaseLoadCount();
    });

    this.subscription.add(
      this.router.events.pipe(filter((e) => e instanceof RouteConfigLoadStart)).subscribe((e) => {
        this.loadCount++;
      })
    );

    this.subscription.add(
      this.router.events.pipe(filter((e) => e instanceof RouteConfigLoadEnd)).subscribe((e) => {
        this.decreaseLoadCount();
      })
    );
  }

  private decreaseLoadCount() {
    this.loadCount--;
    if (this.loadCount < 0) {
      this.loadCount = 0;
    }
  }

  showLoadIndicator(): boolean {
    // at least one pending lazy load operation...
    return !!this.loadCount;
  }
}
