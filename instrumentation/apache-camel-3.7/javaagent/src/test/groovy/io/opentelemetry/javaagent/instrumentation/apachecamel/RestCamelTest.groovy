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
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.SpanKind.SERVER
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP

class RestCamelTest extends AgentInstrumentationSpecification implements RetryOnAddressAlreadyInUseTrait {

  @Shared
  ConfigurableApplicationContext server
  @Shared
  int port

  def setupSpec() {
    withRetryOnAddressAlreadyInUse({
      setupSpecUnderRetry()
    })
  }

  def setupSpecUnderRetry() {
    port = PortUtils.findOpenPort()
    def app = new SpringApplication(RestConfig)
    app.setDefaultProperties(["restServer.port": port])
    server = app.run()
    println getClass().name + " http server started at: http://localhost:$port/"
  }

  def cleanupSpec() {
    if (server != null) {
      server.close()
      server = null
    }
  }

  def "rest component - server and client call with jetty backend"() {
    setup:
    def camelContext = server.getBean(CamelContext)
    ProducerTemplate template = camelContext.createProducerTemplate()

    when:
    // run client and server in separate threads to simulate "real" rest client/server call
    new Thread(new Runnable() {
      @Override
      void run() {
        template.asyncRequestBodyAndHeaders("direct:start", null, ["module": "firstModule", "unitId": "unitOne"])
      }
    }
    ).start()

    then:
    assertTraces(1) {
      trace(0, 8) {
        it.span(0) {
          name "direct"
          kind CLIENT
          attributes {
            "camel.uri" "direct://start"
            "component" "camel-direct"
          }
        }
        it.span(1) {
          name "direct"
          kind CLIENT
          parentSpanId(span(0).spanId)
          attributes {
            "camel.uri" "direct://start"
            "component" "camel-direct"
          }
        }
        it.span(2) {
          name "direct"
          kind INTERNAL
          event(0, {
            eventName("log")
          })
          event(1, {
            eventName("log")
          })
          parentSpanId(span(1).spanId)
          attributes {
            "camel.uri" "direct://start"
            "component" "camel-direct"
          }
        }
        it.span(3) {
          name "rest"
          kind CLIENT
          parentSpanId(span(2).spanId)
          attributes {
            "camel.uri" "rest://get:api/%7Bmodule%7D/unit/%7BunitId%7D"
            "component" "camel-rest"
          }
        }
        it.span(4) {
          name '/*'
          kind SERVER
          parentSpanId(span(3).spanId)
          attributes {
             "http.flavor" "1.1"
             "http.method" "GET"
             "http.route" "/*"
             "http.scheme" "http"
             "http.status_code" 200
             "http.target" "/api/firstModule/unit/unitOne"
             "http.user_agent" String
             "net.host.name" String
             "net.host.port" Long
             "net.sock.host.addr" String
             "net.sock.peer.addr" String
             "net.sock.peer.port" Long
             "net.transport" "ip_tcp"
          }
        }
        it.span(5) {
          name 'rest'
          kind SERVER
          attributes {
            "camel.uri" "rest://get:/api:/%7Bmodule%7D/unit/%7BunitId%7D?consumerComponentName=jetty&producerComponentName=http&routeId=route3"
            "component" "camel-rest"
          }
        }
        it.span(6) {
          name 'direct'
          kind CLIENT
          attributes {
            "camel.uri" "direct://moduleUnit"
            "component" "camel-direct"
          }
        }
        it.span(7) {
          name 'direct'
          kind INTERNAL
          attributes {
            "camel.uri" "direct://moduleUnit"
            "component" "camel-direct"
          }
        }
      }
    }
  }
}
