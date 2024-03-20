import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Observable, forkJoin, map, of, skipWhile, switchMap, take, timer } from 'rxjs';
import { ActionBroadcastDto, ActionExecution, ActionScope } from 'src/app/models/gen.dtos';
import { InstancesService } from 'src/app/modules/primary/instances/services/instances.service';
import { ActionsService } from '../../services/actions.service';

@Component({
  selector: 'app-bd-actions',
  templateUrl: './bd-actions.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BdActionsComponent {
  private instances = inject(InstancesService);
  protected actions = inject(ActionsService);

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
      // this is the observable to wait for instance to be loaded.
      const inst = this.instances.instances$.pipe(
        skipWhile((i) => !i),
        take(1),
        map((i) => i.find((x) => x.instanceConfiguration.id === dto.action.instance)),
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
          map(filteredJoiner),
        );
      } else {
        return inst.pipe(
          map((i) => [dto.description, dto.action.bhive, i.instanceConfiguration.name, dto.action.item]),
          map(filteredJoiner),
        );
      }
    }
  }

  protected formatDuration(exec: ActionExecution) {
    const ms = new Date().getTime() - exec.start;
    const sec = Math.floor(ms / 1000) % 60;
    const min = Math.floor(ms / 60000) % 60;
    const hours = Math.floor(ms / 3600000) % 24;
    const days = Math.floor(ms / 86400000);

    let s = '';
    if (days > 0) {
      s = s + days + (days === 1 ? ' day ' : ' days ');
    }
    if (hours > 0 || days > 0) {
      s = s + hours + (hours === 1 ? ' hour ' : ' hours ');
    }
    if (min > 0 || hours > 0 || days > 0) {
      s = s + min + (min === 1 ? ' minute' : ' minutes');
    }
    if (days === 0 && hours === 0 && min === 0) {
      s = s + sec + (sec === 1 ? ' second' : ' seconds');
    }
    return s;
  }
}
