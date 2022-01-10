import { HttpContext } from '@angular/common/http';
import { NGX_LOADING_BAR_IGNORED } from '@ngx-loading-bar/http-client';

/** A HttpHeaders compatible object removing the loading bar from a request (for background reloads, etc.) */
export const NO_LOADING_BAR_CONTEXT = new HttpContext().set(NGX_LOADING_BAR_IGNORED, true);

/** An options object for the HttpClient which provides headers which disable the loading bar */
export const NO_LOADING_BAR = { context: NO_LOADING_BAR_CONTEXT };
