# P8 — External HTTP timeout policy

## Decision

PlanB uses Spring Boot's global HTTP client settings for every Boot-managed
`WebClient.Builder`:

- connect timeout: `5s`
- read timeout: `15s`

Production can tune the values through `EXTERNAL_HTTP_CONNECT_TIMEOUT` and
`EXTERNAL_HTTP_READ_TIMEOUT`. No per-client timeout adapter or custom
`WebClient.Builder` bean is added.

The 15-second read timeout keeps the existing `NutritionService` lookup budget and
its observed 4–6 second normal response window intact. The 5-second connect timeout
bounds connection establishment separately. Both defaults are conservative because
the external providers publish no response-time SLA and the current production
percentile distribution is unavailable.

## Behavior

Spring Boot 4.0.2 maps:

- `spring.http.clients.connect-timeout` to Reactor Netty
  `ChannelOption.CONNECT_TIMEOUT_MILLIS`
- `spring.http.clients.read-timeout` to Reactor Netty
  `HttpClient.responseTimeout(Duration)`

The read timeout is a maximum interval between network reads while receiving a
response. It is not a total orchestration deadline. Existing business validation,
application retry policy, and exception mapping remain unchanged.

## References

- [Spring Boot — Calling REST Services](https://docs.spring.io/spring-boot/4.0/reference/io/rest-client.html)
- [Spring Boot — Common Application Properties](https://docs.spring.io/spring-boot/4.0/appendix/application-properties/index.html)
- [Spring Boot 4.0.2 — ReactorHttpClientBuilder](https://github.com/spring-projects/spring-boot/blob/v4.0.2/module/spring-boot-http-client/src/main/java/org/springframework/boot/http/client/ReactorHttpClientBuilder.java#L102-L123)
- [Reactor Netty — Response Timeout](https://projectreactor.io/docs/netty/release/reference/http-client.html#_response_timeout)
