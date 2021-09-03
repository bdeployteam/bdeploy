import { Component, OnInit } from '@angular/core';
import { SettingsService } from 'src/app/modules/core/services/settings.service';

@Component({
  selector: 'app-general-tab',
  templateUrl: './general-tab.component.html',
  styleUrls: ['./general-tab.component.css'],
})
export class GeneralTabComponent implements OnInit {
  constructor(public settings: SettingsService) {}

  ngOnInit(): void {}
}
