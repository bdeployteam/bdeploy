import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../core/core.module';
import { InstanceGroupModule } from '../instance-group/instance-group.module';
import { SharedModule } from '../shared/shared.module';
import { ConfigFilesBrowserComponent } from './components/config-files-browser/config-files-browser.component';
import { ConfigFilesRoutingModule } from './config-files-routing.module';

@NgModule({
  declarations: [ConfigFilesBrowserComponent],
  imports: [CommonModule, SharedModule, CoreModule, InstanceGroupModule, ConfigFilesRoutingModule],
})
export class ConfigFilesModule {}
