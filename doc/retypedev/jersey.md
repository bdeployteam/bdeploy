---
order: 1
---
# Jersey based remote infrastructure

This project provides the required abstractions and framework parts for the remote stack (HTTPS based client/server).

The stack is composed of:

* [Grizzly HTTPS server](https://javaee.github.io/grizzly/)
* [Jersey JAX-RS 2.1 implementation](https://jersey.github.io/)
* [Jersey SSE (Server Sent Events) Extension](https://jersey.github.io/documentation/latest/sse.html)
* [Jersey WebResourceFactory (dynamic proxy client)](https://allegro.tech/2015/05/JAX-RS-client-api.html)
* [HK2 Dependency injection kernel](https://javaee.github.io/hk2/)
* [Jackson JSON ObjectMapper](https://github.com/FasterXML/jackson)
* [Jackson JDK8 and JSR310 support](https://github.com/FasterXML/jackson-modules-java8)

## Troubleshooting

1. JerseyServer registers a writer and reader which transparently maps `Path` objects to streams of their content. This works only if the endpoint `@Produces` or `@Consumes` a media type `application/octet-stream`. Don't forget to add that media type. In case you do forget, the actual `Path` instead of it's content will be transferred.
2. The auditing filter which writes all service call audit logs reacts on the **response**, which means that you need to take that into account when associating Hive audit logs with request on the container.