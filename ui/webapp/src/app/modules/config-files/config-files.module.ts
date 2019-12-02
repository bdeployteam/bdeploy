import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { AceEditorModule } from 'ng2-ace-editor';
import { CoreModule } from '../core/core.module';
import { SharedModule } from '../shared/shared.module';
import { ConfigFilesBrowserComponent } from './components/config-files-browser/config-files-browser.component';
import { ConfigFilesRoutingModule } from './config-files-routing.module';

@NgModule({
  declarations: [
    ConfigFilesBrowserComponent
  ],
  imports: [
    CommonModule,
    SharedModule,
    CoreModule,
    AceEditorModule,
    ConfigFilesRoutingModule
  ]
})
export class ConfigFilesModule { }
