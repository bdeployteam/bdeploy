import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { PasswordStrengthMeterComponent } from 'angular-password-strength-meter';
import { provideZxvbnServiceForPSM } from 'angular-password-strength-meter/zxcvbn';
import { CoreModule } from '../../core/core.module';
import { EditComponent } from './components/settings/edit/edit.component';
import { PasswordComponent } from './components/settings/password/password.component';
import { SettingsComponent } from './components/settings/settings.component';
import { TokenComponent } from './components/settings/token/token.component';
import { ThemesComponent } from './components/themes/themes.component';
import { UserRoutingModule } from './user-routing.module';

@NgModule({
  declarations: [ThemesComponent, SettingsComponent, EditComponent, TokenComponent, PasswordComponent],
  imports: [
    CommonModule,
    CoreModule,
    UserRoutingModule,
    PasswordStrengthMeterComponent,
    MatFormFieldModule,
    MatInputModule,
  ],
  providers: [provideZxvbnServiceForPSM()],
})
export class UserModule {}
