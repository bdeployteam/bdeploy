import { Component, OnInit } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { filter, map } from 'rxjs/operators';
import { HeaderTitleService } from './modules/core/services/header-title.service';
import { Logger, LoggingService } from './modules/core/services/logging.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css'],
})
export class AppComponent implements OnInit {
  log: Logger = this.loggingService.getLogger('AppComponent');

  title = 'webapp';

  constructor(
    private loggingService: LoggingService,
    private router: Router,
    private activatedRoute: ActivatedRoute,
    private titleService: Title,
    private headerService: HeaderTitleService,
  ) {
    this.log.info('----------------------------------------');
    this.log.info(this.title + ' started...');
    this.log.info('----------------------------------------');
  }

  ngOnInit() {
    this.router.events
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
        let title = 'BDeploy';
        if (url.data && url.data.title) {
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
  }
}
