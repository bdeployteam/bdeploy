---
order: 3
---
# Plugins

**BDeploy** has plugin support, for now mainly to contribute custom parameter editors, for example to contribute an encryption algorithm to a password input field, etc.

From a low level viewpoint, plugins (as of now) may:

* Contribute JAX-RS endpoints to the server side. These endpoints are provided in a dedicated JAX-RS application on the server, so basically all kinds of Providers, Features, Filters and Resources can be registered on the plugins behalf.
* Contribute static assets. Those are served from a dedicated location per-plugin, and can host all kinds of static resources, including the required ES6 JavaScript modules used to provide custom editor UI.
* Define custom editors and provide the matching ES6 module through the static resources mentioned above.

:::{align=center}
![Plugins](/images/plugins.svg){width=480}
:::

1. The server will first load any global plugins that are installed. When loading a plugin, it is internally registered and a dedicated namespace is assigned where resources are served. The server will register the plugins JAX-RS endpoints and all static assets provided by the plugin under this context path. They are then directly accessible from the outside.
2. The Web UI will query for available custom editors once a parameter specifies it requires one (see `app-info.yaml` -> `customEditor` in the end user documentation).
3. The Web UI's plugin service will query the backend for plugins which can provide the requested editor(s). This will also demand-load plugins which are provided by the product backing the instance currently being edited. This works the same as for global plugins above.
4. Assuming the backend returned a plugin providing a suitable custom editor, the custom editor metadata includes the path to the JavaScript (ES6) module to load. The module is loaded into the Web application.
5. Then it is instantiated and passed an API object which can be used to transparently access the plugins exposed resources (JAX-RS API, static assets).
6. The custom editor is bound to the Web UI on demand and can now perform edit operations on parametes, using its backend resources as required.

## Plugin Distribution

Plugins are distributed and loaded in two ways:

* A global plugin is a JAR file which is added to the server either through the command line or its UI. Global plugins provide functionality to be used by everything on the server globally on demand.
* A product-bound (local) plugin is delivered through the product by configuring it in the `product-info.yaml`. See the user documentation for details.

## How to create a plugin

A template to begin from is provided [in the GitHub repository](https://github.com/bdeployteam/bdeploy/tree/master/plugins). It contains all the bits required:

* A plugin project must compile against the BDelpoy API JAR. This can be found either [in the GitHub packages Maven repository](https://github.com/bdeployteam/bdeploy/packages/234722), or on the [releases page as plain JAR](https://github.com/bdeployteam/bdeploy/releases).
* A plugin must extend `io.bdeploy.api.plugin.v1.Plugin` class. It may override methods to provide its resources to **BDeploy**.
* A plugin must specify some important MANIFEST.MF headers which help the **BDeploy** plugin system with identifying plugins:
  * `BDeploy-Plugin`: This header should contain the fully qualified class name of the class extending the `io.bdeploy.api.plugin.v1.Plugin` class.
  * `BDeploy-PluginName`: A human readable name for the plugin helping administrators identifying the plugin.
  * `BDeploy-PluginVersion`: A version string, helping administrators identifying the plugins version.
* A plugin can contain JAX-RS resource for the backend
* A plugin can contain ES6 JavaScript modules which provide custom editor UI for the user. In theory, you can use whatever you like, as long as it has a single JavaScript entrypoint file which can be loaded from the UI (e.g. Stencil WebComponents have been tested - you still need to provide the single entrypoint class though).
  * The ES6 module must contain a default exported class, so **BDeploy** can instantiate the plugin withough knowing its class names.

## The JavaScript Plugin API

The **Java** API is pretty straight forward, and can be seen immediately when looking at the `io.bdeploy.api.plugin.v1.Plugin` class. It is a little bit different for the JavaScript API, as there are no interfaces you can look at. Internally, **BDeploy** uses TypeScript and thus it _has_ an interface definition.

### The Custom Editor API

The interface for a custom editor looks like this:

```typescript
export interface EditorPlugin {
  new(api: Api); <1>
  bind(onRead: () => string, onUpdate: (value: string) => void, onValidStateChange: (valid: boolean) => void): HTMLElement; <2>
}
```

A custom editor definiton in the plugins main class points to an ES6 module, whos default class must implement this interface.

1. The constructor accepts an [API object](/plugins/#the-api-object), which can be used to interface with the JAX-RS resources of the plugin.
2. The bind method receives callbacks for communication with the configuration web UI, and returns an HTMLElement which has to be created by the plugin.

### The API Object

The API Object is passed to the plugins constructor. It allows communication with the plugins backend, without the need for the plugin to know where exactly this API is hosted on the server.

```typescript
export interface Api {
  get(path: string, params?: {[key: string]: string}): Promise<any>; <1>
  put(path: string, body: any, params?: {[key: string]: string}): Promise<any>;
  post(path: string, body: any, params?: {[key: string]: string}): Promise<any>;
  delete(path: string, params?: {[key: string]: string}): Promise<any>;
  getResourceUrl(): string; <2>
}
```

1. The `get`, `put`, `post` and `delete` methods can be used to issue according requests to the plugins JAX-RS resources.
2. The resource URL can be used to load static resources. The URL will be the base URL where static and JAX-RS resources are registered.
