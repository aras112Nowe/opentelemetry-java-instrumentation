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

class DirectCamelTest extends AgentInstrumentationSpecification {

  @Shared
  ConfigurableApplicationContext server

  def setupSpec() {
    def app = new SpringApplication(DirectConfig)
    server = app.run()
  }

  @Override
  def cleanup() {

    if (server != null) {
      server.close()
      server = null
    }
  }

  def cleanupSpec() {
    if (server != null) {
      server.close()
      server = null
    }
  }

  def "simple direct to a single services"() {
    setup:
    def camelContext = server.getBean(CamelContext)
    ProducerTemplate template = camelContext.createProducerTemplate()

    when:
    template.asyncRequestBody("direct:input", "Example request")

    then:
    assertTraces(1) {
      trace(0, 5) {
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
          parentSpanId parent.span(0).spanId
          attributes {
            "camel.uri" "direct://input"
            "component" "camel-direct"
          }
        }
        it.span(2) {
          name "direct"
          kind INTERNAL
          parentSpanId parent.span(1).spanId
          attributes {
            "camel.uri" "direct://input"
            "component" "camel-direct"
          }
          event(0, {
            eventName("log")
          })
          event(1, {
            eventName("log")
          })
        }
        it.span(3) {
          name "direct"
          kind CLIENT
          parentSpanId parent.span(2).spanId
          attributes {
            "camel.uri" "direct://receiver"
            "component" "camel-direct"
          }
        }
        it.span(4) {
          name "direct"
          kind INTERNAL
          event(0, {
            eventName("log")
          })
          parentSpanId parent.span(3).spanId
          attributes {
            "camel.uri" "direct://receiver"
            "component" "camel-direct"
          }
        }
      }
    }
  }
}
