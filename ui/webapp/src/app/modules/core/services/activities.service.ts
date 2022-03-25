import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { cloneDeep, isEqual } from 'lodash-es';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import {
  ActivitySnapshot,
  ObjectChangeDetails,
  ObjectChangeType,
  ObjectScope,
} from '../../../models/gen.dtos';
import { AuthenticationService } from './authentication.service';
import { ConfigService } from './config.service';
import { NavAreasService } from './nav-areas.service';
import { EMPTY_SCOPE, ObjectChangesService } from './object-changes.service';

export class ActivitySnapshotTreeNode {
  constructor(
    public snapshot: ActivitySnapshot,
    public children: ActivitySnapshotTreeNode[]
  ) {}
}

@Injectable({
  providedIn: 'root',
})
export class ActivitiesService {
  public activities$ = new BehaviorSubject<ActivitySnapshotTreeNode[]>(null);

  private changesSubscription: Subscription;

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    areas: NavAreasService,
    changes: ObjectChangesService,
    auth: AuthenticationService
  ) {
    combineLatest([areas.groupContext$, areas.instanceContext$])
      .pipe(debounceTime(500))
      .subscribe(([group, instance]) => {
        const scope: ObjectScope = cloneDeep(EMPTY_SCOPE);
        if (group) {
          scope.scope.push(group);

          if (instance) {
            scope.scope.push(instance);
          }
        }

        if (this.changesSubscription) {
          this.changesSubscription.unsubscribe();
        }

        // reset in case there are no more matching activities in the new scope.
        this.activities$.next([]);

        if (
          !auth.isGlobalAdmin() &&
          (scope.scope.length === 0 || auth.isCurrentScopeExclusiveReadClient())
        ) {
          // in this case we would see *all* activities from *all* scopes. this is not only a performance
          // but also a permission-wise problem, as permissions are granted on group level (first level of scope).
          // global admins STILL want to see all activities, e.g. when performing maintenance on global scope.
          return;
        }

        this.changesSubscription = changes.subscribe(
          ObjectChangeType.ACTIVITIES,
          scope,
          (c) => {
            this.activities$.next(
              this.getActivitiesFromEvent(
                c.details[ObjectChangeDetails.ACTIVITIES],
                scope.scope
              )
            );
          }
        );
      });
  }

  public cancelActivity(uuid: string) {
    return this.http.delete(this.cfg.config.api + '/activities/' + uuid);
  }

  public getActivitiesFromEvent(
    data: any,
    scope: string[]
  ): ActivitySnapshotTreeNode[] {
    const allActivities = JSON.parse(data) as ActivitySnapshot[];

    if (!allActivities?.length) {
      return [];
    }

    const allTreeNodes = new Map<string, ActivitySnapshotTreeNode>();
    const rootNodes: ActivitySnapshotTreeNode[] = [];

    // create a node for each activity.
    allActivities
      .map((a) => new ActivitySnapshotTreeNode(a, []))
      .forEach((n) => allTreeNodes.set(n.snapshot.uuid, n));

    // wire up nodes with each other and find root nodes which are interesting.
    allTreeNodes.forEach((n) => {
      if (n.snapshot.parentUuid) {
        const parentNode = allTreeNodes.get(n.snapshot.parentUuid);
        if (!parentNode) {
          console.warn(
            'Cannot find referenced parent activity for: ' +
              JSON.stringify(n.snapshot)
          );
        } else {
          parentNode.children.push(n);
        }
      } else {
        // it's a root node, check if we're interested in it
        const a = n.snapshot;
        if (!scope || scope.length === 0) {
          rootNodes.push(n);
          return; // interested in all
        }

        if (!a.scope) {
          return; // no scope, but interested in scoped
        }

        if (a.scope.length < scope.length) {
          return; // less scope than requested. can never match
        }

        // only compare as many scope elements as requested. this allows
        // listening to enclosing scopes to catch operations of all children.
        const trimmedScope = a.scope.slice(0, scope.length);
        if (isEqual(trimmedScope, scope)) {
          rootNodes.push(n);
        }
      }
    });

    return rootNodes;
  }

  public getMostRelevantMessage(node: ActivitySnapshotTreeNode): string {
    // recurse down, always pick the /last/ child.
    if (node.children && node.children.length > 0) {
      return this.getMostRelevantMessage(
        node.children[node.children.length - 1]
      );
    }

    if (!node.snapshot) {
      return null;
    }

    if (node.snapshot.max <= 0) {
      if (node.snapshot.current > 0) {
        return `${node.snapshot.name} (${node.snapshot.current})`;
      } else {
        return node.snapshot.name;
      }
    } else {
      return `${node.snapshot.name} (${node.snapshot.current}/${node.snapshot.max})`;
    }
  }
}
