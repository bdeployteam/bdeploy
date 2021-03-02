import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { ThemesComponent } from './components/themes/themes.component';

const USER_ROUTES: Route[] = [{ path: 'themes', component: ThemesComponent }];

@NgModule({
  imports: [RouterModule.forChild(USER_ROUTES)],
  exports: [RouterModule],
})
export class UserRoutingModule {}
