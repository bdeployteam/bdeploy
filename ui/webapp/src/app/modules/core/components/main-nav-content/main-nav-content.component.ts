import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { isString } from 'lodash-es';
import { filter, map } from 'rxjs/operators';
import { routerAnimation } from '../../animations/special';
import { NavAreasService } from '../../services/nav-areas.service';

@Component({
  selector: 'app-main-nav-content',
  templateUrl: './main-nav-content.component.html',
  styleUrls: ['./main-nav-content.component.css'],
  animations: [routerAnimation],
})
export class MainNavContentComponent implements OnInit {
  constructor(private router: Router, private activatedRoute: ActivatedRoute, public areas: NavAreasService) {}

  animationState: string;

  ngOnInit(): void {
    this.router.events
      .pipe(
        filter((e) => e instanceof NavigationEnd),
        map(() => this.activatedRoute),
        map((route) => {
          while (route.firstChild && route.firstChild.outlet === 'primary') {
            route = route.firstChild;
          }
          return route;
        }),
        filter((route) => route.outlet === 'primary'),
        map((route) => route.snapshot)
      )
      .subscribe((route) => {
        this.animationState = isString(route.component) ? route.component : route.component.name;
      });
  }
}
