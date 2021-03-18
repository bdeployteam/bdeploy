import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { ClientApplicationsComponent } from './components/client-applications/client-applications.component';
import { GroupsBrowserComponent } from './components/groups-browser/groups-browser.component';
import { GroupsRoutingModule } from './groups-routing.module';

@NgModule({
  declarations: [GroupsBrowserComponent, ClientApplicationsComponent],
  imports: [CommonModule, CoreModule, GroupsRoutingModule],
})
export class GroupsModule {}
