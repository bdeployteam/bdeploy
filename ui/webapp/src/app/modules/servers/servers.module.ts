import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../core/core.module';
import { SharedModule } from '../shared/shared.module';
import { AttachCentralComponent } from './components/attach-central/attach-central.component';
import { AttachManagedComponent } from './components/attach-managed/attach-managed.component';
import { ManagedServerDetailComponent } from './components/managed-server-detail/managed-server-detail.component';
import { ManagedServersComponent } from './components/managed-servers/managed-servers.component';
import { ProductSyncComponent } from './components/product-sync/product-sync.component';
import { ServersRoutingModule } from './servers-routing.module';



@NgModule({
  declarations: [
    AttachCentralComponent,
    AttachManagedComponent,
    ManagedServersComponent,
    ManagedServerDetailComponent,
    ProductSyncComponent
  ],
  imports: [
    CommonModule,
    SharedModule,
    CoreModule,
    ServersRoutingModule,
  ]
})
export class ServersModule { }
