/**
 * Copyright (C) 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.redhat.ipaas.rest.v1.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

import java.io.Serializable;
import java.util.Optional;

/**
 * A ChangeEvent is used to notify clients about changes
 * to rest API resources.
 */
@Value.Immutable
@JsonDeserialize(builder = ImmutableEventMessage.Builder.class)
public interface EventMessage extends ToJson, Serializable {

    Optional<String> getEvent();
    EventMessage withEvent(String kind);

    Optional<Object> getData();
    EventMessage withData(Object kind);

    static EventMessage of(String event, Object data) {
        return ImmutableEventMessage.builder().event(event).data(data).build();
    }

}
