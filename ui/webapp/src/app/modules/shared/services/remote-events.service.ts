import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { isEqual } from 'lodash';
import ReconnectingWebSocket from 'reconnecting-websocket';
import { ActivitySnapshot, WebSocketInitDto } from '../../../models/gen.dtos';
import { AuthenticationService } from '../../core/services/authentication.service';
import { ConfigService } from '../../core/services/config.service';
import { LoggingService } from '../../core/services/logging.service';

export class ActivitySnapshotTreeNode {
  constructor(public snapshot: ActivitySnapshot, public children: ActivitySnapshotTreeNode[]) {}
}

@Injectable({
  providedIn: 'root',
})
export class RemoteEventsService {

  private log = this.loggingService.getLogger('RemoteEventService');

  constructor(private cfg: ConfigService, private auth: AuthenticationService, private http: HttpClient, private loggingService: LoggingService) {}

  public cancelActivity(uuid: string) {
    return this.http.delete(this.cfg.config.api + '/activities/' + uuid);
  }

  public createActivitiesWebSocket(scope: string[]) {
    return this.createAuthenticatedWebSocket('/activities', scope);
  }

  public createInstanceUpdatesWebSocket(scope: string[]) {
    return this.createAuthenticatedWebSocket('/instance-updates', scope);
  }

  public createAttachEventsWebSocket() {
    return this.createAuthenticatedWebSocket('/attach-events', []);
  }

  private createAuthenticatedWebSocket(path: string, scope: string[]) {
    const ws = new ReconnectingWebSocket(this.cfg.getWsUrl() + path);
    ws.addEventListener('open', () => {
      const init: WebSocketInitDto = {
        token: this.auth.getToken(),
        scope: scope
      };
      ws.send(JSON.stringify(init));
    });
    return ws;
  }

  public parseEvent(data: any, scope: string[]): ActivitySnapshotTreeNode[] {
    const allActivities = JSON.parse(data) as ActivitySnapshot[];
    const allTreeNodes = new Map<string, ActivitySnapshotTreeNode>();
    const rootNodes: ActivitySnapshotTreeNode[] = [];

    // create a node for each activity.
    allActivities.map(a => new ActivitySnapshotTreeNode(a, [])).forEach(n => allTreeNodes.set(n.snapshot.uuid, n));

    // wire up nodes with each other and find root nodes which are interesting.
    allTreeNodes.forEach(n => {
      if (n.snapshot.parentUuid) {
        const parentNode = allTreeNodes.get(n.snapshot.parentUuid);
        if (!parentNode) {
          this.log.warn('Cannot find referenced parent activity for: ' + JSON.stringify(n.snapshot));
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
}

