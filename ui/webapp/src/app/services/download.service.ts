import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class DownloadService {
  /**
   * Opens the well known open dialog to open/save a file.
   */
  public downloadData(name: string, data: any) {
    const mediatype = 'application/octet-stream';
    const blob = new Blob([JSON.stringify(data)], { type: mediatype });

    const link = document.createElement('a');
    link.href = window.URL.createObjectURL(blob);
    link.download = name;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }
}
