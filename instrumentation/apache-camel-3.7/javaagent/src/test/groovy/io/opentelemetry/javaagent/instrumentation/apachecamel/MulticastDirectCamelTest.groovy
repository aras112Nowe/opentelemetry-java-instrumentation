/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import org.apache.camel.CamelContext
import org.apache.camel.ProducerTemplate
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.*

class MulticastDirectCamelTest extends AgentInstrumentationSpecification {

  @Shared
  ConfigurableApplicationContext server

  def setupSpec() {
    def app = new SpringApplication(MulticastConfig)
    server = app.run()
  }

  def cleanupSpec() {
    if (server != null) {
      server.close()
      server = null
    }
  }

  def "parallel multicast to two child services"() {
    setup:
    def camelContext = server.getBean(CamelContext)
    ProducerTemplate template = camelContext.createProducerTemplate()

    when:
    template.asyncRequestBody("direct:input", "Example request")

    then:
    assertTraces(1) {
      trace(0, 7) {
        def parent = it
        it.span(0) {
          name "direct"
          kind CLIENT
          hasNoParent()
          attributes {
            "camel.uri" "direct://input"
            "component" "camel-direct"
          }
        }
        it.span(1) {
          name "direct"
          kind CLIENT
          attributes {
            "camel.uri" "direct://input"
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
          attributes {
            "camel.uri" "direct://input"
            "component" "camel-direct"
          }
        }
        it.span(3) {
          name "direct"
          kind CLIENT
          attributes {
            "camel.uri" String
            "component" "camel-direct"
          }
        }
        it.span(4) {
          name "direct"
          kind INTERNAL
          event(0, {
            eventName("log")
          })
          attributes {
            "camel.uri" String
            "component" "camel-direct"
          }
        }
        it.span(5) {
          name "direct"
          kind CLIENT
          attributes {
            "camel.uri" String
            "component" "camel-direct"
          }
        }
        it.span(6) {
          name "direct"
          event(0, {
            eventName("log")
          })
          kind INTERNAL
          attributes {
            "camel.uri" String
            "component" "camel-direct"
          }
        }
      }
    }
  }
}
