import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { cloneDeep, isEqual } from 'lodash-es';
import { BehaviorSubject, Observable, Subscription, combineLatest, of, startWith } from 'rxjs';
import { debounceTime, distinctUntilChanged, map, skipWhile, take } from 'rxjs/operators';
import {
  Action,
  ActionBroadcastDto,
  ActionExecution,
  Actions,
  ObjectChangeDetails,
  ObjectChangeType,
  ObjectEvent,
  ObjectScope,
} from '../../../models/gen.dtos';
import { AuthenticationService } from './authentication.service';
import { ConfigService } from './config.service';
import { NavAreasService } from './nav-areas.service';
import { EMPTY_SCOPE, ObjectChangesService } from './object-changes.service';

@Injectable({
  providedIn: 'root',
})
export class ActionsService {
  private cfg = inject(ConfigService);
  private http = inject(HttpClient);
  private areas = inject(NavAreasService);
  private changes = inject(ObjectChangesService);
  private auth = inject(AuthenticationService);

  public actions$ = new BehaviorSubject<ActionBroadcastDto[]>(null);

  private apiPath = `${this.cfg.config.api}/actions`;
  private changesSubscription: Subscription;

  constructor() {
    combineLatest([
      this.areas.groupContext$,
      this.areas.repositoryContext$,
      this.areas.instanceContext$,
      this.cfg.offline$.pipe(distinctUntilChanged()),
      this.auth.getTokenSubject().pipe(distinctUntilChanged()),
    ])
      .pipe(debounceTime(500))
      .subscribe(([group, repo, instance, offline]) => {
        const scope: ObjectScope = cloneDeep(EMPTY_SCOPE);
        if (group) {
          scope.scope.push(group);

          if (instance) {
            scope.scope.push(instance);
          }
        } else if (repo) {
          scope.scope.push(repo);
        }

        this.changesSubscription?.unsubscribe();

        // when updating, we need to re-fetch the current state of actions.
        this.actions$.next(null);

        if (offline) {
          return; // don't bother.
        }

        if (!this.auth.isGlobalAdmin() && (scope.scope.length === 0 || this.auth.isCurrentScopeExclusiveReadClient())) {
          // in this case we would see *all* actions from *all* scopes. this is not only a performance
          // but also a permission-wise problem, as permissions are granted on group level (first level of scope).
          // global admins STILL want to see all actions, e.g. when performing maintenance on global scope.
          return;
        }

        let params = new HttpParams();
        if (!!group) {
          params = params.set('group', group);
        } else if (!!repo) {
          params = params.set('group', repo);
        }
        if (!!instance) {
          params = params.set('instance', instance);
        }
        this.http.get<ActionBroadcastDto[]>(`${this.apiPath}`, { params }).subscribe((r) => {
          this.actions$.next(r);
        });

        this.changesSubscription = this.changes.subscribe(ObjectChangeType.SERVER_ACTIONS, scope, (c) => {
          this.actions$
            .pipe(
              skipWhile((a) => !a),
              take(1)
            )
            .subscribe((actions) => {
              if (c.event === ObjectEvent.CREATED) {
                this.addAction(actions, JSON.parse(c.details[ObjectChangeDetails.SERVER_ACTION]));
              } else if (c.event === ObjectEvent.REMOVED) {
                this.removeAction(actions, JSON.parse(c.details[ObjectChangeDetails.SERVER_ACTION]));
              }
            });
        });
      });
  }

  private isSameAction(a: Action, b: Action): boolean {
    return isEqual(a, b);
  }

  private isSameExecution(a: ActionExecution, b: ActionExecution): boolean {
    return isEqual(a, b);
  }

  private isSame(a: ActionBroadcastDto, b: ActionBroadcastDto) {
    return this.isSameAction(a.action, b.action) && this.isSameExecution(a.execution, b.execution);
  }

  private addAction(current: ActionBroadcastDto[], dto: ActionBroadcastDto) {
    if (current.findIndex((e) => this.isSame(e, dto)) !== -1) {
      // we have the exact action already. we're done.
      return;
    }

    if (dto.exclusive && current.findIndex((e) => this.isSameAction(e.action, dto.action)) !== -1) {
      console.log(`Exclusive action already present with different execution`, dto);
      return;
    }

    this.actions$.next([...current, dto]);
  }

  private removeAction(current: ActionBroadcastDto[], dto: ActionBroadcastDto) {
    // include all elements that are not the one to remove.
    this.actions$.next(current.filter((e) => !this.isSame(e, dto)));
  }

  public action(
    /**
     * Which action should be listened for.
     */
    types: Actions[],

    /**
     * An observable which will be used in *addition* to the server action state to produce a boolean value
     * which indicates whether any of the requested actions is running.
     */
    localState: Observable<boolean>,

    /**
     * A list of groups which are matched on server actions to determine whether the action is running.
     * If not given, the *current* group is used as single value.
     */
    groups?: Observable<string | string[]>,

    /**
     * A list of instances which are matched on server actions to determine whether the action is running.
     * If not given, the *current* instance is used as single value.
     */
    instance?: Observable<string | string[]>,

    /**
     * A list of items which are matched on server actions to determine whether the action is running.
     * If not given, the item field on events is ignored and any value will match.
     */
    item?: Observable<string | string[]>
  ) {
    // take into account all given observables of the request and provide defaults in case they are not set.
    const groupObs = groups || this.areas.groupContext$;
    const instObs = instance || this.areas.instanceContext$;
    const itemObs = item || of(null);

    // filter all server knwon actions and map them. let them through if they match any of the conditions.
    const serverObs = combineLatest([
      this.actions$.pipe(map((a) => a?.filter((x) => types.includes(x.action.type)))),
      groupObs,
      instObs,
      itemObs,
    ]).pipe(
      skipWhile(([actions]) => !actions?.length),
      map(([actions, groups, instances, items]) =>
        actions.filter((a) => this.actionMatches(a, groups, instances, items))
      ),
      map((a) => !!a?.length)
    );

    // now provide a combined observable for local and server state
    return combineLatest([localState, serverObs.pipe(startWith(false))]).pipe(map(([a, b]) => a || b));
  }

  private actionMatches(
    action: ActionBroadcastDto,
    groups: string | string[] | null,
    instances: string | string[] | null,
    items: string | string[] | null
  ): boolean {
    return (
      this.actionAttributeMatch(action.action.bhive, groups) &&
      this.actionAttributeMatch(action.action.instance, instances) &&
      this.actionAttributeMatch(action.action.item, items)
    );
  }

  private actionAttributeMatch(actionAttribute: string, allowedAttributes: string | string[] | null): boolean {
    if (!allowedAttributes) {
      return true; // ignore check if allowed = null;
    }

    if (!actionAttribute) {
      return false; // the action has this attribute not set, but an allowed list/attribute exists -> no match.
    }

    if (Array.isArray(allowedAttributes)) {
      return allowedAttributes.includes(actionAttribute);
    } else {
      return allowedAttributes === actionAttribute;
    }
  }
}
