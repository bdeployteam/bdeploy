import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
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
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatSortModule } from '@angular/material/sort';
import { MatStepperModule } from '@angular/material/stepper';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatTreeModule } from '@angular/material/tree';
import { RouterModule } from '@angular/router';
import { CoreModule } from '../../core/core.module';
import { SharedModule } from '../../legacy/shared/shared.module';
import { InstanceGroupCardComponent } from './components/instance-group-card/instance-group-card.component';
import { InstanceGroupLogoComponent } from './components/instance-group-logo/instance-group-logo.component';
import { InstanceGroupTitleComponent } from './components/instance-group-title/instance-group-title.component';
import { ManagedServerUpdateComponent } from './components/managed-server-update/managed-server-update.component';
import { ProductCardComponent } from './components/product-card/product-card.component';
import { ProductInfoCardComponent } from './components/product-info-card/product-info-card.component';
import { ProductListComponent } from './components/product-list/product-list.component';
import { ProductTagCardComponent } from './components/product-tag-card/product-tag-card.component';
import { UserEditComponent } from './components/user-edit/user-edit.component';
import { UserPasswordComponent } from './components/user-password/user-password.component';
import { UserPickerComponent } from './components/user-picker/user-picker.component';

@NgModule({
  declarations: [
    ManagedServerUpdateComponent,
    InstanceGroupCardComponent,
    InstanceGroupLogoComponent,
    ProductTagCardComponent,
    ProductInfoCardComponent,
    InstanceGroupTitleComponent,
    ProductCardComponent,
    ProductListComponent,
    UserEditComponent,
    UserPasswordComponent,
    UserPickerComponent,
  ],
  imports: [
    CommonModule,
    CoreModule,
    SharedModule,
    RouterModule,
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
    FormsModule,
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
    MatSidenavModule,
  ],
  exports: [
    ManagedServerUpdateComponent,
    InstanceGroupCardComponent,
    InstanceGroupLogoComponent,
    InstanceGroupTitleComponent,
    ProductTagCardComponent,
    ProductInfoCardComponent,
    ProductCardComponent,
    ProductListComponent,
    UserPickerComponent,

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
    FormsModule,
    ReactiveFormsModule,

    // things no longer exported by the new core module.
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
    MatSidenavModule,
  ],
})
export class CoreLegacyModule {}
