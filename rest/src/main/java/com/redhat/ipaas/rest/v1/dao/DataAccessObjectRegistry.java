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
package com.redhat.ipaas.rest.v1.dao;

import com.redhat.ipaas.rest.v1.model.WithId;
import com.redhat.ipaas.rest.v1.controller.handler.exception.IPaasServerException;

import java.util.Map;

public interface DataAccessObjectRegistry {

    Map<Class, DataAccessObject> getDataAccessObjectMapping();

    /**
     * Finds the {@link DataAccessObject} for the specified type.
     * @param type  The class of the specified type.
     * @param <T>   The specified type.
     * @return      The {@link DataAccessObject} if found, or null no matching {@link DataAccessObject} was found.
     */
    default <T extends WithId> DataAccessObject<T> getDataAccessObject(Class<T> type) {
        return getDataAccessObjectMapping().get(type);
    }


    /**
     * Finds the {@link DataAccessObject} for the specified type.
     * @param type  The class of the specified type.
     * @param <T>   The specified type.
     * @return      The {@link DataAccessObject} or throws {@link IPaasServerException}.
     */
    default <T extends WithId> DataAccessObject<T> getDataAccessObjectRequired(Class<T> type) {
        DataAccessObject dao = getDataAccessObjectMapping().get(type);
        if (dao != null) {
            return (DataAccessObject<T>) getDataAccessObjectMapping().get(type);
        }
        throw new IllegalArgumentException("No data access object found for type: [" + type + "].");
    }

    /**
     * Regiester a {@link DataAccessObject}.
     * @param dataAccessObject  The {@link DataAccessObject} to register.
     * @param <T>               The type of the {@link DataAccessObject}.
     */
    default <T extends WithId> void registerDataAccessObject(DataAccessObject<T> dataAccessObject) {
        getDataAccessObjectMapping().put(dataAccessObject.getType(), dataAccessObject);
    }
}
