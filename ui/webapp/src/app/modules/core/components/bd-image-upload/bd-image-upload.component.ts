import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';

@Component({
    selector: 'app-bd-image-upload',
    templateUrl: './bd-image-upload.component.html',
    styleUrls: ['./bd-image-upload.component.css'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [MatIconButton, MatIcon]
})
export class BdImageUploadComponent implements OnInit {
  private readonly sanitizer = inject(DomSanitizer);

  @Input() disabled = false;
  @Input() image: SafeUrl;
  @Output() imageSelected = new EventEmitter<File>();
  @Output() unsupportedDrop = new EventEmitter<File>();

  private origImage: SafeUrl;

  ngOnInit(): void {
    this.origImage = this.image;
  }

  protected resetImage(): void {
    this.image = this.origImage;
    this.imageSelected.emit(null);
  }

  protected onImageChange(event) {
    const reader = new FileReader();
    if (event.target.files && event.target.files.length > 0) {
      const selLogoFile: File = event.target.files[0];
      reader.onload = () => {
        const selLogoUrl: string = reader.result.toString();
        if (
          selLogoUrl.startsWith('data:image/jpeg') ||
          selLogoUrl.startsWith('data:image/png') ||
          selLogoUrl.startsWith('data:image/gif') /*||
          selLogoUrl.startsWith('data:image/svg+xml')*/
        ) {
          this.image = this.sanitizer.bypassSecurityTrustUrl(selLogoUrl);
          this.imageSelected.emit(selLogoFile);
        } else {
          this.unsupportedDrop.emit(selLogoFile);
        }
      };
      reader.readAsDataURL(selLogoFile);
    }
  }
}
