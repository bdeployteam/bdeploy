import { Component, OnInit } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, NavigationEnd, RouteConfigLoadEnd, RouteConfigLoadStart, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { HeaderTitleService } from './modules/core/services/header-title.service';
import { Logger, LoggingService } from './modules/core/services/logging.service';
import { RoutingHistoryService } from './modules/core/services/routing-history.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css'],
})
export class AppComponent implements OnInit {
  log: Logger = this.loggingService.getLogger('AppComponent');

  title = 'webapp';

  subscription: Subscription;
  loadCount = 0;

  constructor(
    private loggingService: LoggingService,
    private router: Router,
    private activatedRoute: ActivatedRoute,
    private titleService: Title,
    private headerService: HeaderTitleService,
    private routingHistoryService: RoutingHistoryService
  ) {
    this.log.info('----------------------------------------');
    this.log.info(this.title + ' started...');
    this.log.info('----------------------------------------');
  }

  ngOnInit() {
    this.subscription = this.router.events
      .pipe(
        filter(e => e instanceof NavigationEnd),
        map(() => this.activatedRoute),
        map(route => {
          while (route.firstChild) {
            route = route.firstChild;
          }
          return route;
        }),
        filter(route => route.outlet === 'primary'),
        map(route => route.snapshot)
      )
      .subscribe(url => {
        // safety, reduce load count, route is loaded so it cannot be loading anymore :)
        // this can happen if lazy loading is initiated but a guard redirects the router
        // somewhere else.
        this.decreaseLoadCount();

        let title = 'BDeploy';
        if (url.data && url.data.title) {
          // ATTENTION: params is used in the titles of routes (eval below).
          const params = url.params;
          // tslint:disable-next-line:no-eval
          const expanded = eval('`' + url.data.title + '`');
          title += ` - ${expanded}`;
        }
        if (url.data && url.data.header) {
          // explicit other header than title.
          this.headerService.setHeaderTitle(url.data.header); // no variable expansion for now.
        } else {
          // make sure header is taken from title.
          this.headerService.setHeaderTitle(null);
        }
        this.titleService.setTitle(title);
      });

    this.subscription.add(this.router.events.pipe(
      filter(e => e instanceof RouteConfigLoadStart)
    ).subscribe(e => {
      this.loadCount++;
    }));

    this.subscription.add(this.router.events.pipe(
      filter(e => e instanceof RouteConfigLoadEnd)
    ).subscribe(e => {
      this.decreaseLoadCount();
    }));
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
