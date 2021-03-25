/** A HttpHeaders compatible object removing the loading bar from a request (for background reloads, etc.) */
export const NO_LOADING_BAR_HDRS = { ignoreLoadingBar: '' };

/** An options object for the HttpClient which provides headers which disable the loading bar */
export const NO_LOADING_BAR = { headers: NO_LOADING_BAR_HDRS };
