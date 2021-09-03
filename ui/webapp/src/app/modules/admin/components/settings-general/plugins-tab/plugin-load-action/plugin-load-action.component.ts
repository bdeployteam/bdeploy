import { Component, Input, OnInit } from '@angular/core';
import { PluginInfoDto } from 'src/app/models/gen.dtos';
import { PluginAdminService } from 'src/app/modules/admin/services/plugin-admin.service';

@Component({
  selector: 'app-plugin-load-action',
  templateUrl: './plugin-load-action.component.html',
  styleUrls: ['./plugin-load-action.component.css'],
})
export class PluginLoadActionComponent implements OnInit {
  @Input() record: PluginInfoDto;

  constructor(private plugins: PluginAdminService) {}

  ngOnInit(): void {}

  /* template */ doLoadUnload() {
    if (this.record.loaded) {
      this.plugins.unloadPlugin(this.record);
    } else {
      this.plugins.loadGlobalPlugin(this.record);
    }
  }
}
