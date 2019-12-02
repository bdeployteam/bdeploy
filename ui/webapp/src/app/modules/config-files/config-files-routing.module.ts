import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { AuthGuard } from '../shared/guards/authentication.guard';
import { CanDeactivateGuard } from '../shared/guards/can-deactivate.guard';
import { ConfigFilesBrowserComponent } from './components/config-files-browser/config-files-browser.component';

const CFG_ROUTES: Route[] = [
  {
    path: 'browser/:group/:uuid/:version',
    component: ConfigFilesBrowserComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Configuration Files (${params["group"]} - ${params["uuid"]})', header: 'Configuration Files' }
  },
];

@NgModule({
  imports: [RouterModule.forChild(CFG_ROUTES)],
  exports: [RouterModule],
})
export class ConfigFilesRoutingModule { }
