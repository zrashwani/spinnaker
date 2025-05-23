/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.slack

import com.netflix.spinnaker.echo.api.Notification
import com.netflix.spinnaker.echo.api.Notification.InteractiveActions
import com.netflix.spinnaker.echo.controller.EchoResponse
import com.netflix.spinnaker.echo.notification.NotificationService
import com.netflix.spinnaker.echo.notification.NotificationTemplateEngine
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Slf4j
@Component
@ConditionalOnProperty('slack.enabled')
class SlackNotificationService implements NotificationService {

  protected SlackService slack
  protected NotificationTemplateEngine notificationTemplateEngine

  SlackNotificationService(
    @Qualifier("slackLegacyService") SlackService slack,
    NotificationTemplateEngine notificationTemplateEngine
  ) {
    this.slack = slack
    this.notificationTemplateEngine = notificationTemplateEngine
  }

  @Override
  boolean supportsType(String type) {
    return "SLACK".equals(type.toUpperCase())
  }

  @Override
  EchoResponse.Void handle(Notification notification) {
    log.debug("Handling Slack notification to ${notification.to}")
    def subject = notificationTemplateEngine.build(notification, NotificationTemplateEngine.Type.SUBJECT)
    def text = notificationTemplateEngine.build(notification, NotificationTemplateEngine.Type.BODY)
    notification.to.each {
      def response
      String address = it
      if (slack.config.sendCompactMessages) {
        response = slack.sendCompactMessage(new CompactSlackMessage(text), address, true)
      } else {
        response = slack.sendMessage(
          new SlackAttachment(subject, text, (InteractiveActions)notification.interactiveActions),
          address, true)
      }
      try {
        log.trace("Received response from Slack for message '{}'. {}", text, response?.string())
      } catch (IOException e) {
        log.trace("Received response from Slack for message '{}' but unable to deserialize", text, e)
      }

    }

    new EchoResponse.Void()
  }
}
