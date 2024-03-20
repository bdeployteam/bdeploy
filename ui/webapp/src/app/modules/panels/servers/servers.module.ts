import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatExpansionModule } from '@angular/material/expansion';
import { CoreModule } from '../../core/core.module';
import { LinkCentralComponent } from './components/link-central/link-central.component';
import { LinkManagedComponent } from './components/link-managed/link-managed.component';
import { ServerDetailsComponent } from './components/server-details/server-details.component';
import { ServerEditComponent } from './components/server-details/server-edit/server-edit.component';
import { ServersRoutingModule } from './servers-routing.module';

@NgModule({
  declarations: [LinkCentralComponent, LinkManagedComponent, ServerDetailsComponent, ServerEditComponent],
  imports: [CommonModule, CoreModule, ServersRoutingModule, MatExpansionModule, MatCardModule],
})
export class ServersModule {}
