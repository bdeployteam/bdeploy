// the Api interface is passed to the plugins constructor. it holds callbacks which can perform API calls to the plugin's server side.
// this way the plugin does not need to know how to perform REST calls at all and can be independent of angular, etc.
export interface Api {
  get(path: string, params?: {[key: string]: string}): Promise<any>;
  put(path: string, body: any, params?: {[key: string]: string}): Promise<any>;
  post(path: string, body: any, params?: {[key: string]: string}): Promise<any>;
  delete(path: string, params?: {[key: string]: string}): Promise<any>;
  getResourceUrl(): string;
}
