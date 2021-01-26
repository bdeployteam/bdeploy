import { Injectable } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { isString } from 'lodash-es';
import { BehaviorSubject } from 'rxjs';
import { filter, map } from 'rxjs/operators';

@Injectable({
  providedIn: 'root',
})
export class NavAreasService {
  panelVisible = new BehaviorSubject<boolean>(false);
  panelMaximized = new BehaviorSubject<boolean>(false);
  menuMaximized = new BehaviorSubject<boolean>(false);

  private primaryState: string;

  constructor(private router: Router, private activatedRoute: ActivatedRoute) {
    this.router.events
      .pipe(
        filter((e) => e instanceof NavigationEnd),
        map(() => this.activatedRoute)
      )
      .subscribe((route) => {
        // SOMEthing changed in the routing, some navigation happened. we need to find out which outlet changed
        // 1. if the panel outlet is visible, and no panel route is active, hide the outlet.
        // 2. if the panel outlet is not visible, and a panel route is active, show the outlet.
        // 3. if a panel route is active, set the expanded state according to its data.
        // 4. if the primary outlet changed, navigate the panel outlet to 'null' to hide it.

        // the two potential activated routes are *direct* childs of the main route. no need to recurse.
        const primary = this.findChildRouteForOutlet(route, 'primary');
        const panel = this.findChildRouteForOutlet(route, 'panel');

        // the *actual* component route which is displayed in the according outlet may *not* have the outlet
        // property set to the requested outlet - only the first child needs to have that.
        const primarySnapshot = this.findRouteLeaf(primary)?.snapshot;
        const panelSnapshot = this.findRouteLeaf(panel)?.snapshot;

        // update the states visible to the flyin part of the main nav.
        this.panelVisible.next(panelSnapshot ? true : false);
        this.panelMaximized.next(panelSnapshot && panelSnapshot.data && panelSnapshot.data['max']);

        // if the component (name) in the primary outlet changed, we want to leave the panel navigation.
        const newPrimaryState = isString(primarySnapshot.component)
          ? primarySnapshot.component
          : primarySnapshot.component.name;

        // primaryState may not be set in case we are just navigating from the void, i.e. somebody opened a link
        // which includes a panel navigation.
        if (this.primaryState && newPrimaryState !== this.primaryState) {
          this.router.navigate(['', { outlets: { panel: null } }]);
        }
        this.primaryState = newPrimaryState;
      });

    if (localStorage.getItem('menu') === null) {
      localStorage.setItem('menu', 'false');
    }

    const expand = localStorage.getItem('menu') === 'true';
    this.menuMaximized.next(expand);
    this.menuMaximized.subscribe((v) => {
      // store the current value in the local storage, so it persist across reloads.
      localStorage.setItem('menu', v ? 'true' : 'false');
    });
  }

  private findRouteLeaf(route: ActivatedRoute): ActivatedRoute {
    if (!route) {
      return null;
    }
    let result = route;
    while (result.firstChild) {
      result = result.firstChild;
    }
    return result;
  }

  private findChildRouteForOutlet(route: ActivatedRoute, outlet: string): ActivatedRoute {
    if (!route.children) {
      return null;
    }

    for (const child of route.children) {
      if (child.outlet === outlet) {
        return child;
      }
    }
  }
}
