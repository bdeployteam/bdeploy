import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { ServersRoutingModule } from './servers-routing.module';
import { ServersBrowserComponent } from './components/servers-browser/servers-browser.component';

@NgModule({
  declarations: [ServersBrowserComponent],
  imports: [CommonModule, CoreModule, ServersRoutingModule],
})
export class ServersModule {}
