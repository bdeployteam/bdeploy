import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { MessageboxService } from '../../services/messagebox.service';
import { MessageBoxMode } from '../messagebox/messagebox.component';

@Component({
  selector: 'app-bd-image-upload',
  templateUrl: './bd-image-upload.component.html',
  styleUrls: ['./bd-image-upload.component.css'],
})
export class BdImageUploadComponent implements OnInit {
  @Input() disabled = false;
  @Input() image: SafeUrl;
  @Output() imageSelected = new EventEmitter<File>();

  private origImage: SafeUrl;

  constructor(private sanitizer: DomSanitizer, private messageBox: MessageboxService) {}

  ngOnInit(): void {
    this.origImage = this.image;
  }

  /* template */ resetImage(): void {
    this.image = this.origImage;
    this.imageSelected.emit(null);
  }

  /* template */ onImageChange(event) {
    const reader = new FileReader();
    if (event.target.files && event.target.files.length > 0) {
      const selLogoFile: File = event.target.files[0];
      console.log('INIT ', selLogoFile);
      reader.onload = () => {
        const selLogoUrl: string = reader.result.toString();
        console.log('READ ', selLogoFile);
        if (
          selLogoUrl.startsWith('data:image/jpeg') ||
          selLogoUrl.startsWith('data:image/png') ||
          selLogoUrl.startsWith('data:image/gif') ||
          selLogoUrl.startsWith('data:image/svg+xml')
        ) {
          this.image = this.sanitizer.bypassSecurityTrustUrl(selLogoUrl);
          this.imageSelected.emit(selLogoFile);
        } else {
          this.messageBox.open({
            title: 'Unsupported Image Type',
            message: 'Please choose a different image. Supported types: jpeg, png, gif or svg',
            mode: MessageBoxMode.ERROR,
          });
        }
      };
      reader.readAsDataURL(selLogoFile);
    }
  }
}
