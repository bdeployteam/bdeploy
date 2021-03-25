import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatBadgeModule } from '@angular/material/badge';
import { MatBottomSheetModule } from '@angular/material/bottom-sheet';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatRippleModule } from '@angular/material/core';
import { MatDialogModule } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatGridListModule } from '@angular/material/grid-list';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatSortModule } from '@angular/material/sort';
import { MatStepperModule } from '@angular/material/stepper';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatTreeModule } from '@angular/material/tree';
import { NgTerminalModule } from 'ng-terminal';
import { MonacoEditorModule } from 'ngx-monaco-editor';
import { CoreModule } from '../../core/core.module';
import { InstanceService } from '../instance/services/instance.service';
import { CustomAttributeEditComponent } from './components/custom-attribute-edit/custom-attribute-edit.component';
import { CustomAttributeGroupingSelectorComponent } from './components/custom-attribute-grouping-selector/custom-attribute-grouping-selector.component';
import { CustomAttributeValueComponent } from './components/custom-attribute-value/custom-attribute-value.component';
import { DiffEditorComponent } from './components/diff-editor/diff-editor.component';
import { FileEditorComponent } from './components/file-editor/file-editor.component';
import { FileUploadComponent } from './components/file-upload/file-upload.component';
import { FileViewerComponent } from './components/file-viewer/file-viewer.component';
import { MessageboxComponent } from './components/messagebox/messagebox.component';
import { RemoteProgressElementComponent } from './components/remote-progress-element/remote-progress-element.component';
import { RemoteProgressComponent } from './components/remote-progress/remote-progress.component';
import { TextboxComponent } from './components/textbox/textbox.component';
import { MessageboxService } from './services/messagebox.service';
import { PortValidatorDirective } from './validators/port.validator';

@NgModule({
  declarations: [
    RemoteProgressComponent,
    RemoteProgressElementComponent,
    FileViewerComponent,
    FileUploadComponent,
    PortValidatorDirective,
    CustomAttributeEditComponent,
    CustomAttributeValueComponent,
    CustomAttributeGroupingSelectorComponent,
    FileEditorComponent,
    DiffEditorComponent,
    TextboxComponent,
    MessageboxComponent,
  ],
  providers: [MessageboxService, InstanceService],
  imports: [
    CommonModule,
    CoreModule,
    NgTerminalModule,
    MatButtonToggleModule,
    MatGridListModule,
    MatMenuModule,
    MatChipsModule,
    MatDialogModule,
    MatExpansionModule,
    MatBottomSheetModule,
    MatPaginatorModule,
    MatStepperModule,
    MatRadioModule,
    ReactiveFormsModule,

    MatButtonModule,
    MatToolbarModule,
    MatIconModule,
    MatListModule,
    MatSnackBarModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTableModule,
    MatDividerModule,
    MatAutocompleteModule,
    MatTooltipModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatCheckboxModule,
    MatRippleModule,
    MatSlideToggleModule,
    MatBadgeModule,
    MatTreeModule,
    MatSortModule,
    MatTabsModule,
    MonacoEditorModule.forRoot(),
  ],
  exports: [
    RemoteProgressComponent,
    FileViewerComponent,
    FileUploadComponent,
    PortValidatorDirective,
    CustomAttributeEditComponent,
    CustomAttributeValueComponent,
    CustomAttributeGroupingSelectorComponent,
    FileEditorComponent,
    DiffEditorComponent,
    TextboxComponent,
    MessageboxComponent,
    MatButtonToggleModule,
    MatGridListModule,
    MatMenuModule,
    MatChipsModule,
    MatDialogModule,
    MatExpansionModule,
    MatBottomSheetModule,
    MatPaginatorModule,
    MatStepperModule,
    MatRadioModule,
    ReactiveFormsModule,

    MatButtonModule,
    MatToolbarModule,
    MatIconModule,
    MatListModule,
    MatSnackBarModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTableModule,
    MatDividerModule,
    MatAutocompleteModule,
    MatTooltipModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatCheckboxModule,
    MatRippleModule,
    MatSlideToggleModule,
    MatBadgeModule,
    MatTreeModule,
    MatSortModule,
    MatTabsModule,
  ],
})
export class SharedModule {}
