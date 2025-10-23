import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Observable, forkJoin, map, of, skipWhile, switchMap, take, timer } from 'rxjs';
import { ActionBroadcastDto, ActionExecution, ActionScope } from 'src/app/models/gen.dtos';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ActionsService } from '../../services/actions.service';
import { BdNotificationCardComponent } from '../bd-notification-card/bd-notification-card.component';
import { MatProgressBar } from '@angular/material/progress-bar';
import { BdNoDataComponent } from '../bd-no-data/bd-no-data.component';
import { AsyncPipe } from '@angular/common';

@Component({
  selector: 'app-bd-actions',
  templateUrl: './bd-actions.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BdNotificationCardComponent, MatProgressBar, BdNoDataComponent, AsyncPipe]
})
export class BdActionsComponent {
  private readonly instances = inject(InstancesService);
  private readonly groups = inject(GroupsService);
  protected readonly actions = inject(ActionsService);

  // to update duration values every second.
  content$ = timer(0, 1000).pipe(switchMap(() => this.actions.actions$.pipe(take(1))));

  protected doTrack(index: number, item: ActionBroadcastDto) {
    return `${item.action.type}.${item.action.bhive}.${item.action.instance}.${item.action.item}.${item.execution.name}.${item.execution.start}`;
  }

  protected formatTitle(dto: ActionBroadcastDto): Observable<string> {
    const filteredJoiner = (components: string[]) => components.filter((c) => !!c).join(' - ');

    if (dto.scope === ActionScope.GLOBAL) {
      return of([dto.description, dto.action.item]).pipe(map(filteredJoiner));
    } else if (dto.scope === ActionScope.BHIVE) {
      return of([dto.description, dto.action.bhive, dto.action.item]).pipe(map(filteredJoiner));
    } else {
      // if we are in global scope (i.e. not inside an instance group), we cannot rely on instance information at all :(
      // thus we need to show the raw data. This is ok, since we're only showing those to admins anyway!
      if (!this.groups.current$.value) {
        return of([dto.description, dto.action.bhive, dto.action.instance, dto.action.item]).pipe(map(filteredJoiner));
      }

      // this is the observable to wait for instance to be loaded.
      const inst = this.instances.instances$.pipe(
        skipWhile((i) => !i),
        take(1),
        map((i) => i.find((x) => x.instanceConfiguration.id === dto.action.instance))
      );

      // if the item is a process, we want to load this one as well!
      if (dto.scope === ActionScope.PROCESS) {
        return inst.pipe(
          switchMap((i) => forkJoin([of(i), this.instances.loadNodes(i.instanceConfiguration.id, i.instance.tag)])),
          map(([instance, nodes]) => [
            dto.description,
            dto.action.bhive,
            instance.instanceConfiguration.name,
            nodes.nodeConfigDtos
              ?.find((n) => n.nodeConfiguration?.applications?.findIndex((a) => a.id === dto.action.item) !== -1)
              ?.nodeConfiguration?.applications?.find((x) => x.id === dto.action.item)?.name,
          ]),
          map(filteredJoiner)
        );
      } else {
        return inst.pipe(
          map((i) => [dto.description, dto.action.bhive, i.instanceConfiguration.name, dto.action.item]),
          map(filteredJoiner)
        );
      }
    }
  }

  protected formatDuration(exec: ActionExecution) {
    const ms = Date.now() - exec.start;
    const sec = Math.floor(ms / 1_000) % 60;
    const min = Math.floor(ms / 60_000) % 60;
    const hours = Math.floor(ms / 3_600_000) % 24;
    const days = Math.floor(ms / 86_400_000);

    if (days === 0 && hours === 0 && min === 0) {
      return sec + (sec === 1 ? ' second' : ' seconds');
    }

    let s = '';
    if (days > 0) {
      s += days + (days === 1 ? ' day ' : ' days ');
    }
    if (days > 0 || hours > 0) {
      s += hours + (hours === 1 ? ' hour ' : ' hours ');
    }
    return s + min + (min === 1 ? ' minute' : ' minutes');
  }
}
