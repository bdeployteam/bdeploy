---
order: 1
icon: rss
---

# Endpoint Proxying

**BDeploy** supports proxying (tunnelling) to application endpoints as defined in the [app-info.yaml](/power/product/#app-infoyaml).

The application hosting the endpoint to call can be hosted on any node in the system. As long as this node is attached to a master you can reach (directly in `STANDALONE` or `MANAGED` mode, or indirectly in `CENTRAL` mode), you can tunnel there using **BDeploy**.

You will need these information to be able to call an Endpoint of a remote application through the **BDeploy** public API:

- A **BDeploy** _slim_ token to be supplied for an authorization header in the form of `X-BDeploy-Authorization: Bearer <TOKEN>`. You can obtain this token from the Web UI for the logged in user.
- The `id` of the instance group hosting the application you want to tunnel to.
- The `id` of the instance hosting the application you want to tunnel to. You can see this id for instance from the browsers URL when on the instance overview page for the instance in question.
- The `id` of the applications endpoint as defined in the [app-info.yaml](/power/product/#app-infoyaml).
- The `id` of the application which hosts the endpoint identified by the endpoint `id` above.

A typical proxy call using the public API would look like this, when using **cURL**:

```bash
curl -k -H "Accept: **/**" \
    -H "X-BDeploy-Authorization: Bearer <X>" \ <1>
    "https://server/api/public/v1/common/proxy/myEndpoint?BDeploy_group=MyGroup&BDeploy_instance=xxxx-111-xxxx&BDeploy_application=yyyy-111-yyyy" <2> <3> <4> <5> <6>
```

1. **<X>** is a bearer token, which can be obtained from the Web UI (User drop-down menu).
2. **server** is the hostname (and port) of the **BDeploy** master. This may be either a `CENTRAL`, `MANAGED` or `STANDALONE` server, being in charge (directly or indirectly) of the application to tunnel to.
3. **myEndpoint** is the `id` of the endpoint to call as defined in the [app-info.yaml](/power/product/#app-infoyaml) of the hosting application.
4. **MyGroup** is the `id` of the instance group which hosts the application to tunnel to.
5. **xxxx-111-xxxx** is the `id` of the instance which hosts the application to tunnel to.
6. **yyyy-111-yyyy** is the `id` of the application which hosts an endpoint with the given endpoint `id`.

## How to obtain required IDs

There are two ways to obtain required IDs. You can use a pure manual approach and for instance deduce ID's from URLs and the Web UI itself. Or you can use the public API to query **BDeploy** for available objects.

```
http://my-server/#/instances/dashboard/MyGroup/xxxx-111-xxxx
```

From the above URL, you can find the instance group `id` (**MyGroup**) as well as the instance `id` (**xxxx-111-xxxx**). When on the instance dashboard, click the server application you want to tunnel to. The process control panel for that application opens up, and you will see the applications ` Process ID` displayed.

Last thing to manually lookup is the endpoint `id`. Endpoints can be accessed on the instance configuration page by clicking on to application you want to tunnel to and then clicking on [ **Configure Endpoints** ]

:::{align=center}
![Application Edit Panel](/images/Doc_InstanceConfig_Endpoints.png){width=480}
:::

You will be able to read the endpoint `id` and configure properties of the endpoint on this page. The configuration will be used by **BDeploy** when instructed remotely to call that endpoint. **BDeploy** will take care of things like authentication, security, etc. The actual caller will only require access (and permissions) on the master server he is calling into, not to the actual application. Instead, **BDeploy** itself is configured to be authorized to perform the call.

:::{align=center}
![Application Endpoints Configuration](/images/Doc_InstanceConfig_EndpointsConfig.png){width=480}
:::

For automatic lookup, you can use the following **BDeploy** public API endpoints. Those are provided by the master server, same as the actual proxy endpoint:

```bash
# will list instance groups
curl -k -H "Accept: application/json" \
    -H "X-BDeploy-Authorization: Bearer <X>" \ <1>
    "https://server/api/public/v1/instanceGroups"

# will fetch the latest configuration of all instances in an instance group
curl -k -H "Accept: application/json" \
    -H "X-BDeploy-Authorization: Bearer <X>" \
    "https://server/api/public/v1/common/instances?BDeploy_group=MyGroup&latest=true" <2>

# will fetch all endpoints exposed by a certain instance along with the ids of the applications hosting them.
curl -k -H "Accept: application/json" \
    -H "X-BDeploy-Authorization: Bearer <X>" \
    "https://server/api/public/v1/common/endpoints?BDeploy_group=MyGroup&BDeploy_instance=xxxx-111-xxxx" <2> <3>
```

1. **<X>** in all the following **cURL** calls is the bearer token as obtained from the Web UI.
2. **MyGroup** is the name of one of the instance groups as obtained by the first API. You can fetch the `id` of each instance from the returned JSON.
3. **xxxx-111-xxxx** is the instance `id` as obtained by the second API. The returned JSON will include the application `id` hosting the endpoint along with the actual specific configuration of that endpoint (including its `id`).

## UI Endpoints

UI Endpoints can be defined to allow simple access to hosted web applications in a similar manner than client applications. This means that **BDeploy** will provide a link to the web application in much the same way as it provides access to client applications in its own UI, both on the (server) process status panel, as well as on the client applications page.

!!!info Note
The permission to access a UI endpoint through the **BDeploy** UI is `CLIENT`.
!!!

A sample UI endpoint definition may look something like this in the [app-info.yaml](/power/product/#app-infoyaml):

```yaml app-info.yaml
endpoints:
  http:
    - id: "appUi"
      enabled: "{{V:port-param}}"
      path: "/"
      contextPath: "/appUi"
      port: "8080"
      secure: false
      authType: NONE
      type: UI
      proxying: true
```

!!!info Note
The differenciation between `path` and `contextPath` is that while `path` defines the root of the server hosting the application, `contextPath` may be used to add more path segments to the link generated by BDeploy which leads to the application entry point. The `contextPath` is used _only_ to determine the address to open when the application is opened through **BDeploy**.
!!!

The `enabled` flag controls whether the endpoint is configured and visible to the end user. The expression is evaluate agains the current configuration, and the endpoint is hidden in case the `enabled` condition evaluates to `null`, empty, `false` or cannot be resolved at all.

### UI Endpoint Proxying

Much like "normal" [Endpoint Proxying](#endpoint-proxying), **BDeploy** also provides proxying for UI endpoints - when enabled using the `proxying` attribute on the endpoint. A UI endpoint will be reachable on any participating **BDeploy** server (`STANDALONE` only or `CENTRAL` and `MANAGED` if used). **BDeploy** handles all the traffic from and to the actual web application. There is no direct access to the **actual** application required.

!!!info Note
This feature is intended for simple use cases. Advanced use cases may not work as expected (e.g. WebSockets). **BDeploy** uses a simple request/response wrapping internally to forward requests and is **not** a full HTTP proxy.
!!!

!!!info Note
Only users which are logged in to **BDeploy** can access UI endpoints through the proxy mechanism!
!!!
