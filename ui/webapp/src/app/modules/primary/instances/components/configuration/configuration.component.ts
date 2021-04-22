import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { BehaviorSubject, Subscription } from 'rxjs';
import { InstanceDto } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { ObjectChangesService } from 'src/app/modules/core/services/object-changes.service';
import { ServersService } from '../../../servers/services/servers.service';
import { InstancesService } from '../../services/instances.service';

@Component({
  selector: 'app-configuration',
  templateUrl: './configuration.component.html',
  styleUrls: ['./configuration.component.css'],
})
export class ConfigurationComponent implements OnInit, OnDestroy {
  /* template */ narrow$ = new BehaviorSubject<boolean>(true);
  /* tempalte */ instance$ = new BehaviorSubject<InstanceDto>(null);

  private subscription: Subscription;
  private changesSubscription: Subscription;

  constructor(
    public cfg: ConfigService,
    public areas: NavAreasService,
    public instances: InstancesService,
    public servers: ServersService,
    private media: BreakpointObserver,
    private changes: ObjectChangesService
  ) {
    this.subscription = this.media.observe('(max-width:700px)').subscribe((bs) => this.narrow$.next(bs.matches));
  }

  ngOnInit(): void {
    this.instances.current$.subscribe((inst) => {
      if (!!this.changesSubscription) {
        this.changesSubscription.unsubscribe();
      }

      if (!!inst && !!this.instance$.value) {
        // if both are set, we need to compare whether the version changed on the server.
        if (inst.instance.tag !== this.instance$.value.instance.tag) {
          // TODO: warn user
          console.log('Server has a new version!');
        }
      }

      this.instance$.next(inst);
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
