import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { EditComponent } from './components/settings/edit/edit.component';
import { GuideComponent } from './components/settings/guide/guide.component';
import { PasswordComponent } from './components/settings/password/password.component';
import { SettingsComponent } from './components/settings/settings.component';
import { TokenComponent } from './components/settings/token/token.component';
import { ThemesComponent } from './components/themes/themes.component';

const USER_ROUTES: Route[] = [
  { path: 'themes', component: ThemesComponent },
  { path: 'settings', component: SettingsComponent },
  { path: 'settings/edit', component: EditComponent },
  { path: 'settings/password', component: PasswordComponent },
  { path: 'settings/token', component: TokenComponent },
  { path: 'settings/guide', component: GuideComponent },
];

@NgModule({
  imports: [RouterModule.forChild(USER_ROUTES)],
  exports: [RouterModule],
})
export class UserRoutingModule {}
