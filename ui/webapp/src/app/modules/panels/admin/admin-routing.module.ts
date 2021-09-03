import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AddPluginComponent } from './add-plugin/add-plugin.component';

const routes: Routes = [{ path: 'add-plugin', component: AddPluginComponent }];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class AdminRoutingModule {}
