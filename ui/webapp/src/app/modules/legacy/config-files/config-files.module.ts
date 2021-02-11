import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { SharedModule } from '../../legacy/shared/shared.module';
import { CoreLegacyModule } from '../core/core-legacy.module';
import { InstanceGroupModule } from '../instance-group/instance-group.module';
import { ConfigFilesBrowserComponent } from './components/config-files-browser/config-files-browser.component';
import { ConfigFilesRoutingModule } from './config-files-routing.module';

@NgModule({
  declarations: [ConfigFilesBrowserComponent],
  imports: [CommonModule, SharedModule, CoreModule, CoreLegacyModule, InstanceGroupModule, ConfigFilesRoutingModule],
})
export class ConfigFilesModule {}
