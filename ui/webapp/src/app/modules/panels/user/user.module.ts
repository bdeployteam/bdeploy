import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { ThemesComponent } from './components/themes/themes.component';
import { UserRoutingModule } from './user-routing.module';
import { SettingsComponent } from './components/settings/settings.component';
import { EditComponent } from './components/settings/edit/edit.component';
import { TokenComponent } from './components/settings/token/token.component';
import { GuideComponent } from './components/settings/guide/guide.component';
import { PasswordComponent } from './components/settings/password/password.component';

@NgModule({
  declarations: [ThemesComponent, SettingsComponent, EditComponent, TokenComponent, GuideComponent, PasswordComponent],
  imports: [CommonModule, CoreModule, UserRoutingModule],
})
export class UserModule {}
