import { Injectable } from '@angular/core';
import {
  ActivatedRoute,
  ActivatedRouteSnapshot,
  NavigationEnd,
  NavigationExtras,
  Router,
} from '@angular/router';
import { BehaviorSubject, Subscription } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { DirtyableDialog } from '../guards/dirty-dialog.guard';

export type DirtyableKey = 'primary' | 'panel' | 'admin';
@Injectable({
  providedIn: 'root',
})
export class NavAreasService {
  panelVisible$ = new BehaviorSubject<boolean>(false);
  panelMaximized$ = new BehaviorSubject<boolean>(false);
  menuMaximized$ = new BehaviorSubject<boolean>(false);

  primaryRoute$ = new BehaviorSubject<ActivatedRouteSnapshot>(null);
  panelRoute$ = new BehaviorSubject<ActivatedRouteSnapshot>(null);
  adminRoute$ = new BehaviorSubject<ActivatedRouteSnapshot>(null);

  /** should ONLY be used by GroupsService. Subscribe to GroupsService.current$ instead */
  groupContext$ = new BehaviorSubject<string>(null);

  /** should ONLY be used by InstancesService. Subscribe to InstancesService.current$ instead */
  instanceContext$ = new BehaviorSubject<string>(null);

  repositoryContext$ = new BehaviorSubject<string>(null);

  /** should ONLY be used by DirtyDialogGuard. */
  forcePanelClose$ = new BehaviorSubject<boolean>(false);

  _tempNavGroupContext$ = new BehaviorSubject<string>(null);
  _tempNavRepoContext$ = new BehaviorSubject<string>(null);

  private dirtyables: { [P in DirtyableKey]: DirtyableDialog } = {
    panel: null,
    primary: null,
    admin: null,
  };

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
        // 5. in any case, if the main menu is expanded, collapse it.

        // navigation done, clear temporary buffers.
        this._tempNavGroupContext$.next(null);
        this._tempNavRepoContext$.next(null);

        // the two potential activated routes are *direct* childs of the main route. no need to recurse.
        const primary = this.findChildRouteForOutlet(route, 'primary');
        const panel = this.findChildRouteForOutlet(route, 'panel');
        const admin = this.findChildRouteForOutlet(route, 'admin');

        // the *actual* component route which is displayed in the according outlet may *not* have the outlet
        // property set to the requested outlet - only the first child needs to have that.
        const primarySnapshot = this.findRouteLeaf(primary)?.snapshot;
        const panelSnapshot = this.findRouteLeaf(panel)?.snapshot;
        const adminSnapshot = this.findRouteLeaf(admin)?.snapshot;

        // update the states visible to the flyin part of the main nav.
        this.panelVisible$.next(panelSnapshot ? true : false);
        this.panelMaximized$.next(
          !!panelSnapshot && !!panelSnapshot.data && !!panelSnapshot.data['max']
        );

        // we compare the full route from the root of the primary snapshot to determine whether
        // or not we need to close an open panel. if it changes, we close the panel.
        const newPrimaryState = this.getRouteId(primarySnapshot);

        // trigger updates of component names for those interested.
        if (newPrimaryState !== this.primaryState) {
          this.primaryRoute$.next(primarySnapshot);
        }
        this.panelRoute$.next(panelSnapshot);
        this.adminRoute$.next(adminSnapshot);

        // primaryState may not be set in case we are just navigating from the void, i.e. somebody opened a link
        // which includes a panel navigation.
        if (
          !!this.forcePanelClose$.value ||
          (this.primaryState && newPrimaryState !== this.primaryState)
        ) {
          this.closePanel(this.forcePanelClose$.value);
          this.forcePanelClose$.next(false);
        }

        // we need the primary state to detect whether it changes to clear the panel routing. however
        // we set it to null whenever there is *no* panel route active, to allow to go back/forward to
        // such states through the browser back/forward buttons - it will we treated just like any external
        // (e.g. pasted) links.
        this.primaryState = panelSnapshot ? newPrimaryState : null;

        // as a last step, we determine the current route context. this is done by extracting router parameterx
        // with special names.
        const group = primarySnapshot.paramMap.get('group');
        if (this.groupContext$.value !== group) {
          this.repositoryContext$.next(null);
          this.groupContext$.next(group);
          this.instanceContext$.next(null);
        }
        const instance = primarySnapshot.paramMap.get('instance');
        if (this.instanceContext$.value !== instance) {
          this.instanceContext$.next(instance);
        }
        const repository = primarySnapshot.paramMap.get('repository');
        if (this.repositoryContext$.value !== repository) {
          this.repositoryContext$.next(repository);
          this.groupContext$.next(null);
          this.instanceContext$.next(null);
        }

        // collapse the menu whenever something changed.
        this.menuMaximized$.next(false);
      });
  }

  public getRouteId(snapshot: ActivatedRouteSnapshot) {
    return snapshot.pathFromRoot
      .map((r) => r.url)
      .reduce((a, v) => a.concat(v), [])
      .map((s) => s.path)
      .toString();
  }

  public closePanel(force = false) {
    if (force) {
      this.router.navigate(['', { outlets: { panel: null } }], {
        replaceUrl: true,
        state: { ignoreDirtyGuard: true },
      });
    } else {
      this.router.navigate(['', { outlets: { panel: null } }], {
        replaceUrl: true,
      });
    }
  }

  public navigateBoth(
    primary: any[],
    panel: any[],
    primaryExtra?: NavigationExtras,
    panelExtra?: NavigationExtras
  ) {
    this.router.navigate(primary, primaryExtra).then((nav) => {
      if (nav) {
        // need to perform a panel navigation separately to avoid closing the panel and to separate query params.
        this.router.navigate(['', { outlets: { panel } }], panelExtra);
      }
    });
  }

  public registerDirtyable(
    dirtyable: DirtyableDialog,
    type: DirtyableKey
  ): Subscription {
    this.dirtyables[type] = dirtyable;

    return new Subscription(() => {
      this.deregisterDirtyable(dirtyable, type);
    });
  }

  public hasDirtyPanel() {
    if (!this.dirtyables['panel']) {
      return false;
    }

    return this.dirtyables['panel'].isDirty();
  }

  public getDirtyableType(dirtyable: DirtyableDialog): DirtyableKey {
    if (this.dirtyables['panel'] === dirtyable) {
      return 'panel';
    } else if (this.dirtyables['primary'] === dirtyable) {
      return 'primary';
    } else if (this.dirtyables['admin'] === dirtyable) {
      return 'admin';
    }
  }

  public getDirtyable(type: DirtyableKey) {
    return this.dirtyables[type];
  }

  private deregisterDirtyable(dirtyable: DirtyableDialog, type: DirtyableKey) {
    if (this.dirtyables[type] !== dirtyable) {
      console.error(
        'Unexpected dirtyable while deregistering.',
        dirtyable,
        this.dirtyables[type]
      );
    }

    this.dirtyables[type] = null;
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

  private findChildRouteForOutlet(
    route: ActivatedRoute,
    outlet: string
  ): ActivatedRoute {
    if (!route.children) {
      return null;
    }

    for (const child of route.children) {
      if (child.outlet === outlet) {
        return child;
      }

      if (child.children?.length) {
        const result = this.findChildRouteForOutlet(child, outlet);
        if (result) {
          return result;
        }
      }
    }
  }
}
