import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { BehaviorSubject, combineLatest, Subscription } from 'rxjs';
import { CustomAttributeDescriptor } from 'src/app/models/gen.dtos';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { SettingsService } from 'src/app/modules/core/services/settings.service';

@Component({
  selector: 'edit-global-attribute',
  templateUrl: './edit-global-attribute.component.html',
  styleUrls: ['./edit-global-attribute.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditGlobalAttributeComponent implements OnInit, OnDestroy {
  /* template */ tempAttribute: CustomAttributeDescriptor;
  /* template */ initialAttribute: CustomAttributeDescriptor;
  /* template */ tempUsedIds: string[];
  /* template */ loading$ = new BehaviorSubject<boolean>(true);

  private subscription: Subscription;

  constructor(private settings: SettingsService, private areas: NavAreasService) {}

  ngOnInit(): void {
    this.subscription = combineLatest([this.areas.panelRoute$, this.settings.settings$]).subscribe(([route, settings]) => {
      if (!settings || !route?.params || !route.params['attribute']) {
        return;
      }
      this.initialAttribute = settings.instanceGroup.attributes.find((a) => a.name === route.params['attribute']);
      this.tempAttribute = Object.assign({}, this.initialAttribute);
      this.tempUsedIds = settings.instanceGroup.attributes.map((a) => a.name).filter((a) => a !== this.initialAttribute.name);
      this.loading$.next(false);
    });
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /* template */ onSave() {
    this.settings.editGlobalAttribute(this.tempAttribute, this.initialAttribute);
  }
}
