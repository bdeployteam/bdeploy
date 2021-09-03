import { Component, OnInit } from '@angular/core';
import { SettingsService } from 'src/app/modules/core/services/settings.service';

@Component({
  selector: 'app-look-feel-tab',
  templateUrl: './look-feel-tab.component.html',
  styleUrls: ['./look-feel-tab.component.css'],
})
export class LookFeelTabComponent implements OnInit {
  constructor(public settings: SettingsService) {}

  ngOnInit(): void {}
}
