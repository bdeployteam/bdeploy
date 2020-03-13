import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { DragulaModule } from 'ng2-dragula';
import { CoreModule } from '../core/core.module';
import { SharedModule } from '../shared/shared.module';
import { AttachCentralComponent } from './components/attach-central/attach-central.component';
import { AttachManagedComponent } from './components/attach-managed/attach-managed.component';
import { ManagedServerDetailComponent } from './components/managed-server-detail/managed-server-detail.component';
import { ManagedServerEditComponent } from './components/managed-server-edit/managed-server-edit.component';
import { ManagedServersComponent } from './components/managed-servers/managed-servers.component';
import { ProductSyncComponent } from './components/product-sync/product-sync.component';
import { ServersRoutingModule } from './servers-routing.module';
import { ServerUrlValidatorDirective } from './validators/server-url-validator.directive';



@NgModule({
  declarations: [
    AttachCentralComponent,
    AttachManagedComponent,
    ManagedServersComponent,
    ManagedServerDetailComponent,
    ProductSyncComponent,
    ManagedServerEditComponent,
    ServerUrlValidatorDirective
  ],
  entryComponents: [
    ManagedServerEditComponent
  ],
  imports: [
    CommonModule,
    SharedModule,
    CoreModule,
    ServersRoutingModule,
    DragulaModule.forRoot(),
  ]
})
export class ServersModule { }
