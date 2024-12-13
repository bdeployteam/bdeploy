import { Injectable, inject } from '@angular/core';
import { ConfigService } from './config.service';

// defined in index.html directly to be as global as possible.
declare let downloadLocation: {
  // eslint-disable-next-line @typescript-eslint/ban-types
  assign: (url: string) => {};
  // eslint-disable-next-line @typescript-eslint/ban-types
  click: (link: HTMLAnchorElement) => {};
};

/**
 * Note: ALWAYS use DownloadService to trigger a download for tests to be able to intercept the action.
 */
@Injectable({
  providedIn: 'root',
})
export class DownloadService {
  private readonly cfg = inject(ConfigService);

  public buildResponseFileName(productName: string, instanceTemplateName: string) {
    return 'ResponseFile - ' + productName + ' - ' + instanceTemplateName + '.yaml';
  }

  /**
   * Sends the given object as JSON string as download with the given file name
   *
   * @param name the file name the browser should save the file as
   * @param data any object which can be JSON stringify'ed
   */
  public downloadJson(name: string, data: unknown) {
    const mediatype = 'application/json';
    const blob = new Blob([JSON.stringify(data)], { type: mediatype });
    this.downloadBlob(name, blob);
  }

  /**
   * Sends the given YAML string as download with the given file name
   *
   * @param name the file name the browser should save the file as
   * @param data the string to put in the file
   */
  public downloadYaml(name: string, data: string) {
    const mediatype = 'application/yaml';
    const blob = new Blob([data], { type: mediatype });
    this.downloadBlob(name, blob);
  }

  /**
   * Sends the given blob as download with the given file name
   *
   * @param name the file name the browser should save the file as
   * @param blob the Blob data to send as-is
   */
  public downloadBlob(name: string, blob: Blob) {
    const link = document.createElement('a');
    link.href = window.URL.createObjectURL(blob);
    link.download = name;
    document.body.appendChild(link);
    downloadLocation.click(link);
    document.body.removeChild(link);
    // Don't call window.URL.revokeObjectURL(link.href) as it would free the link even though a UI test (for example)
    // might access the object URL asynchronously later on...
  }

  /**
   * Redirects the browser to the given download URL. This will cause the browser to download the
   * file at the location but stay at the current page.
   *
   * @param url the URL to redirect to.
   */
  public download(url: string) {
    downloadLocation.assign(url);
  }

  /**
   * Creates a new URL to download a file that has been prepared by another call.
   */
  public createDownloadUrl(token: string) {
    return this.cfg.config.api + '/download/file/' + token;
  }
}
