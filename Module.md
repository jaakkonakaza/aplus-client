# Module aplus-client

A Kotlin/JVM client for the A+ REST API. Requires Java 25 or newer.
Call its suspend functions from a coroutine. Scala and Java callers can use `AplusBlockingClient`
for synchronous requests with an operation timeout.

# Package fi.aalto.cs.aplus.client

Keep the owning client open until its authentication views finish.

# Package fi.aalto.cs.aplus.client.dto

A+ response fields and submission requests.
