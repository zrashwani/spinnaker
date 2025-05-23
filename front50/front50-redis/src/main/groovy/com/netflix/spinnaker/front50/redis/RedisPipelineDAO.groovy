/*
 * Copyright 2016 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.front50.redis
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException

import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO
import org.springframework.data.redis.core.Cursor
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ScanOptions
import org.springframework.util.Assert
import org.springframework.util.ObjectUtils

class RedisPipelineDAO implements PipelineDAO {

  static final String BOOK_KEEPING_KEY = 'com.netflix.spinnaker:front50:pipelines'

  RedisTemplate<String, Pipeline> redisTemplate

  @Override
  String getPipelineId(String application, String pipelineName) {
    all().find {
      it.application == application && it.name == pipelineName
    }.id
  }

  @Override
  Collection<Pipeline> getPipelinesByApplication(String application, boolean refresh = true) {
    return getPipelinesByApplication(application, null, refresh)
  }

  @Override
  Collection<Pipeline> getPipelinesByApplication(String application, String pipelineNameFilter, boolean refresh = true) {
    all(refresh).findAll {
      /* if the pipeline name filter is empty, we want to treat it as if it doesn't exist
      if isEmpty returns true, the statement will short circuit and return true,
      which effectively means we don't use the filter at all. */
      it.getApplication() == application &&
        (ObjectUtils.isEmpty(pipelineNameFilter) || it.getName().toLowerCase().contains(pipelineNameFilter.toLowerCase()))
    }
  }

  @Override
  public Pipeline getPipelineByName(String application, String pipelineName, boolean refresh) {
    def retval = all(refresh).find {
      it.application == application && it.name == pipelineName
    }
    if (!retval) {
      throw new NotFoundException("No pipeline found with application '${application}', name '${pipelineName}'");
    }

    retval
  }

  @Override
  Pipeline findById(String id) throws NotFoundException {
    def results = redisTemplate.opsForHash().get(BOOK_KEEPING_KEY, id)
    if (!results) {
      throw new NotFoundException("No pipeline found with id '${id}'");
    }
    results
  }

  @Override
  Collection<Pipeline> all() {
    return all(true)
  }

  @Override
  Collection<Pipeline> all(boolean refresh) {
    return redisTemplate.opsForHash()
      .scan(BOOK_KEEPING_KEY, ScanOptions.scanOptions().match('*').build())
      .withCloseable { Cursor<Map> c ->
      c.collect{ it.value }
    }
  }

  @Override
  Collection<Pipeline> history(String id, int maxResults) {
    return [findById(id)]
  }

  @Override
  Pipeline create(String id, Pipeline item) {

    Assert.notNull(item.application, "application field must NOT be null!")
    Assert.notNull(item.name, "name field must NOT be null!")

    item.id = id ?: UUID.randomUUID().toString()

    redisTemplate.opsForHash().put(BOOK_KEEPING_KEY, item.id, item)

    item
  }

  @Override
  void update(String id, Pipeline item) {
    item.lastModified = System.currentTimeMillis()
    create(id, item)
  }

  @Override
  void delete(String id) {
    redisTemplate.opsForHash().delete(BOOK_KEEPING_KEY, id)
  }

  @Override
  void bulkImport(Collection<Pipeline> items) {
    items.each { create(it.id, it) }
  }

  @Override
  boolean isHealthy() {
    try {
      redisTemplate.opsForHash().get("", "")
      return true
    } catch (Exception e) {
      return false
    }
  }
}
