/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.RetryOnAddressAlreadyInUseTrait
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.camel.CamelContext
import org.apache.camel.ProducerTemplate
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.impl.DefaultCamelContext
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.SpanKind.SERVER
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP

class TwoServicesWithDirectClientCamelTest extends AgentInstrumentationSpecification implements RetryOnAddressAlreadyInUseTrait {

  @Shared
  int portOne
  @Shared
  int portTwo
  @Shared
  ConfigurableApplicationContext server
  @Shared
  CamelContext clientContext

  def setupSpec() {
    withRetryOnAddressAlreadyInUse({
      setupSpecUnderRetry()
    })
  }

  def setupSpecUnderRetry() {
    portOne = PortUtils.findOpenPort()
    portTwo = PortUtils.findOpenPort()
    def app = new SpringApplication(TwoServicesConfig)
    app.setDefaultProperties(["service.one.port": portOne, "service.two.port": portTwo])
    server = app.run()
  }

  def createAndStartClient() {
    clientContext = new DefaultCamelContext()
    clientContext.addRoutes(new RouteBuilder() {
      void configure() {
        from("direct:input")
          .log("SENT Client request")
          .to("http://localhost:$portOne/serviceOne")
          .log("RECEIVED Client response")
      }
    })
    clientContext.start()
  }

  def cleanupSpec() {
    if (server != null) {
      server.close()
      server = null
    }
  }

  def "two camel service spans"() {
    setup:
    createAndStartClient()
    ProducerTemplate template = clientContext.createProducerTemplate()

    when:
    template.asyncRequestBody("direct:input", "Example request")

    then:
    assertTraces(1) {
      trace(0, 8) {
        it.span(0) {
          name "direct"
          kind CLIENT
          attributes {
            "camel.uri" "direct://input"
            "component" "camel-direct"
          }
        }
        it.span(1) {
          name "direct"
          kind CLIENT
          parentSpanId(span(0).spanId)
          attributes {
            "camel.uri" "direct://input"
            "component" "camel-direct"
          }
        }
        it.span(2) {
          name "direct"
          kind INTERNAL
          parentSpanId(span(1).spanId)
          attributes {
            "camel.uri" "direct://input"
            "component" "camel-direct"
          }
        }
        it.span(3) {
          name "http"
          kind CLIENT
          parentSpanId(span(2).spanId)
          attributes {
            "camel.uri" String
            "component" "camel-http"
          }
        }
        it.span(4) {
          name "http"
          kind SERVER
          parentSpanId(span(3).spanId)
          attributes {
            "camel.uri" String
            "component" "camel-http"
          }
        }
        it.span(5) {
          name "http"
          kind CLIENT
          parentSpanId(span(4).spanId)
          attributes {
            "camel.uri" String
            "component" "camel-http"
          }
        }
        it.span(6) {
          name "/*"
          kind SERVER
          parentSpanId(span(5).spanId)
          attributes {
             "http.flavor" "1.1"
             "http.method" "POST"
             "http.request_content_length" 27
             "http.route" "/*"
             "http.scheme" "http"
             "http.status_code" 200
             "http.target" "/serviceTwo"
             "http.user_agent" "Apache-HttpClient/4.5.13 (Java/17.0.5)"
             "net.host.name" String
             "net.host.port" Long
             "net.sock.peer.addr" String
             "net.sock.peer.port" Long
             "net.transport" "ip_tcp"
          }
        }
        it.span(7) {
          name "jetty"
          kind SERVER
          attributes {
            "camel.uri" String
            "component" "camel-jetty"
          }
        }
      }
    }
  }
}
