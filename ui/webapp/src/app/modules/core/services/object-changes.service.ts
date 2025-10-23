import { Injectable, inject } from '@angular/core';
import ReconnectingWebSocket, { ErrorEvent } from 'reconnecting-websocket';
import { BehaviorSubject, Subject, Subscription, combineLatest } from 'rxjs';
import { distinctUntilChanged, skipWhile } from 'rxjs/operators';
import {
  ObjectChangeDto,
  ObjectChangeInitDto,
  ObjectChangeRegistrationDto,
  ObjectChangeType,
  ObjectScope,
  RegistrationAction,
} from 'src/app/models/gen.dtos';
import { AuthenticationService } from './authentication.service';
import { ConfigService } from './config.service';

export const EMPTY_SCOPE: ObjectScope = { scope: [] };

interface RemoteRegistration {
  refCount: number;
  type: ObjectChangeType;
  scope: ObjectScope;
}

@Injectable({
  providedIn: 'root',
})
export class ObjectChangesService {
  private readonly cfg = inject(ConfigService);
  private readonly auth = inject(AuthenticationService);

  public errorCount$ = new BehaviorSubject<number>(0);

  private readonly _change$ = new Subject<ObjectChangeDto>();
  private readonly _error$ = new Subject<ErrorEvent>();
  private readonly _open$ = new BehaviorSubject<boolean>(false);
  private _refs: Record<string, RemoteRegistration> = {};
  private _lastError = 0;
  private readonly ws = this.createWebSocket();

  constructor() {
    combineLatest([
      this.auth.getTokenSubject().pipe(distinctUntilChanged()),
      this.cfg.offline$.pipe(distinctUntilChanged()),
    ])
      .pipe(skipWhile(([t, o]) => !t || o))
      .subscribe(() => this.ws.reconnect());
  }

  private createWebSocket() {
    // See io.bdeploy.jersey.ws.change.ObjectChangeWebSocket
    const _socket = new ReconnectingWebSocket(this.getWebsocketUrl() + '/object-changes');
    _socket.addEventListener('open', () => {
      if (!this.auth.getToken()) {
        return;
      }
      const init: ObjectChangeInitDto = {
        token: this.auth.getToken(),
      };
      _socket.send(JSON.stringify(init));
      this._open$.next(true);

      for (const key of Object.keys(this._refs)) {
        // re-register existing registrations on re-connect
        this.ws.send(
          JSON.stringify({
            action: RegistrationAction.ADD,
            type: this._refs[key].type,
            scope: this._refs[key].scope,
          } as ObjectChangeRegistrationDto)
        );
      }

      // in case this is a re-connect, we want to check the server version.
      this.cfg.checkServerVersion();
    });
    _socket.addEventListener('error', (err) => {
      console.error('Error on WebSocket', err);
      this.cfg.markServerOffline();
      this._error$.next(err);
      this.onErrorIncrease();
    });
    _socket.addEventListener('close', () => {
      this._open$.next(false);
      // "close" is essentially an error, as we NEVER want to close the websocket as long as the application is alive.
      this.onErrorIncrease();
    });

    _socket.addEventListener('message', (e) => this.onMessage(e));
    return _socket;
  }

  private onErrorIncrease() {
    // if websockets are alive less than thirty seconds, count as error towards error count.
    if (this.auth.isAuthenticated()) {
      if (this._lastError + 60_000 > Date.now()) {
        this.errorCount$.next(this.errorCount$.value + 1);
      } else {
        this.errorCount$.next(0);
      }
      this._lastError = Date.now();
    }
  }

  private getWebsocketUrl(): string {
    // relative, use browser information to figure out an absolute URL, since WebSockets require this.
    if (this.cfg.config.ws.startsWith('/')) {
      const url = new URL(globalThis.location.href);
      const isHttps = url.protocol === 'https:';
      const wsProtocol = isHttps ? 'wss:' : 'ws:';
      return wsProtocol + url.host + this.cfg.config.ws;
    }
    return this.cfg.config.ws;
  }

  private onMessage(event: MessageEvent<string>) {
    const update = JSON.parse(event.data) as ObjectChangeDto;
    this._change$.next(update);
  }

  private key(type: ObjectChangeType, scope: ObjectScope): string {
    return `${type}|${scope.scope?.length ? scope.scope.join(';') : '[]'}`;
  }

  public subscribe(
    type: ObjectChangeType,
    scope: ObjectScope,
    next: (next: ObjectChangeDto) => void,
    error?: (err: ErrorEvent) => void
  ): Subscription {
    // First determine whether we need to subscribe on the server.
    const key = this.key(type, scope);
    const needSub = !this._refs[key]?.refCount;
    if (needSub) {
      // we're the first, need to subscribe
      this._refs[key] = { refCount: 1, type, scope };
      if (this._open$.value) {
        this.ws.send(
          JSON.stringify({
            action: RegistrationAction.ADD,
            type: type,
            scope: scope,
          } as ObjectChangeRegistrationDto)
        );
      }
    } else {
      // somebody else already using the same key, the server send us changes already.
      this._refs[key].refCount++;
    }

    // Then create a subscription on the change feed which will now include the messages we want to subscribe to.
    const underlyingSubscription = this._change$.subscribe((change) => {
      // filter out type and scope, since the message feed contains *all* changes any service subscribed to.
      if (change.type !== type) {
        return;
      }
      if (!this.isMessageScopeMatching(scope, change.scope)) {
        return;
      }

      // we have a full match, inform the subscriber.
      next(change);
    });

    if (error) {
      underlyingSubscription.add(this._error$.subscribe((err) => error(err)));
    }

    // return a subscription which will unsubscribe both from the change feed and the server if required.
    return new Subscription(() => {
      const needUnsub = this._refs[key]?.refCount === 1;
      if (needUnsub) {
        // we're the last one with this subscription, unsubscribe from the server.
        delete this._refs[key];
        this.ws.send(
          JSON.stringify({
            action: RegistrationAction.REMOVE,
            type: type,
            scope: scope,
          } as ObjectChangeRegistrationDto)
        );
      } else {
        // we're not the last one, so just decrease the ref-count for the server subscription.
        this._refs[key].refCount--;
      }
      // in *any* case, clear the subscription to the change feed.
      underlyingSubscription.unsubscribe();
    });
  }

  private isMessageScopeMatching(reg: ObjectScope, msg: ObjectScope) {
    // not interested if the registration scope is more detailed than the given one
    if (reg.scope.length > msg.scope.length) {
      return false;
    }

    // compare all scope parts. all scope parts we have must be present on the other scope.
    // the other scope is allowed to be more detailed.
    for (let i = 0; i < reg.scope.length; ++i) {
      if (reg.scope[i] !== msg.scope[i]) {
        return false;
      }
    }

    return true;
  }
}
