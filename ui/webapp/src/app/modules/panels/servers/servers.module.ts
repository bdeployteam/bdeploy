import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { LinkCentralComponent } from './components/link-central/link-central.component';
import { LinkManagedComponent } from './components/link-managed/link-managed.component';
import { ServersRoutingModule } from './servers-routing.module';
import { ServerDetailsComponent } from './components/server-details/server-details.component';
import { ServerOsComponent } from './components/server-details/server-os/server-os.component';

@NgModule({
  declarations: [LinkCentralComponent, LinkManagedComponent, ServerDetailsComponent, ServerOsComponent],
  imports: [CommonModule, CoreModule, ServersRoutingModule],
})
export class ServersModule {}
