import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { CustomAttributeDescriptor } from 'src/app/models/gen.dtos';
import { SettingsService } from 'src/app/modules/core/services/settings.service';

@Component({
  selector: 'add-global-attribute',
  templateUrl: './add-global-attribute.component.html',
  styleUrls: ['./add-global-attribute.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddGlobalAttributeComponent implements OnInit {
  /* template */ tempAttribute: CustomAttributeDescriptor;
  /* template */ tempUsedIds: string[];

  constructor(private settings: SettingsService) {}

  ngOnInit(): void {
    this.tempAttribute = { name: '', description: '' };
    this.tempUsedIds = this.settings.settings$.value.instanceGroup.attributes.map((a) => a.name);
  }

  /* template */ onSave() {
    this.settings.addGlobalAttribute(this.tempAttribute);
  }
}
