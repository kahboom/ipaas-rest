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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.ipaas.rest.EventBus;
import com.redhat.ipaas.rest.v1.controller.handler.exception.IPaasServerException;
import com.redhat.ipaas.rest.v1.model.ChangeEvent;
import com.redhat.ipaas.rest.v1.model.ListResult;
import com.redhat.ipaas.rest.v1.model.WithId;
import com.redhat.ipaas.rest.v1.model.connection.Connection;
import com.redhat.ipaas.rest.v1.model.connection.Connector;
import com.redhat.ipaas.rest.v1.model.connection.ConnectorGroup;
import com.redhat.ipaas.rest.v1.model.environment.Environment;
import com.redhat.ipaas.rest.v1.model.environment.EnvironmentType;
import com.redhat.ipaas.rest.v1.model.environment.Organization;
import com.redhat.ipaas.rest.v1.model.integration.*;
import com.redhat.ipaas.rest.v1.model.user.Permission;
import com.redhat.ipaas.rest.v1.model.user.Role;
import com.redhat.ipaas.rest.v1.model.user.User;
import org.infinispan.Cache;
import org.infinispan.manager.CacheContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.persistence.EntityExistsException;
import javax.persistence.EntityNotFoundException;
import java.util.*;
import java.util.function.Function;

@Service
public class DataManager implements DataAccessObjectRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataManager.class.getName());

    private ObjectMapper mapper;
    private CacheContainer caches;
    private final EventBus eventBus;

    @Value("${deployment.file}")
    private String dataFileName;

    private final List<DataAccessObject> dataAccessObjects = new ArrayList<>();
    private final Map<Class, DataAccessObject> dataAccessObjectMapping = new HashMap<>();

    // Constructor to help with testing.
    public DataManager(CacheContainer caches, ObjectMapper mapper, DataAccessObjectProvider dataAccessObjects, String dataFileName) {
        this(caches, mapper, dataAccessObjects, (EventBus)null);
        this.dataFileName = dataFileName;
    }

    // Inject mandatory via constructor injection.
    @Autowired
    public DataManager(CacheContainer caches, ObjectMapper mapper, DataAccessObjectProvider dataAccessObjects, @Nullable  EventBus eventBus) {
        this.mapper = mapper;
        this.caches = caches;
        this.eventBus = eventBus;
        if (dataAccessObjects != null) {
            this.dataAccessObjects.addAll(dataAccessObjects.getDataAccessObjects());
        }
    }

    @PostConstruct
    public void init() {
        if (dataFileName != null) {
            ReadApiClientData reader = new ReadApiClientData();
            try {
                List<ModelData> mdList = reader.readDataFromFile(dataFileName);
                for (ModelData modelData : mdList) {
                    addToCache(modelData);
                }
            } catch (Exception e) {
                throw new IllegalStateException("Cannot read dummy startup data due to: " + e.getMessage(), e);
            }
        }

        for (DataAccessObject dataAccessObject : dataAccessObjects) {
            registerDataAccessObject(dataAccessObject);
        }
    }

    public void addToCache(ModelData modelData) {
        try {
            Class<? extends WithId> clazz;
            clazz = getClass(modelData.getKind());
            Cache<String, WithId> cache = caches.getCache(modelData.getKind().toLowerCase());

            LOGGER.debug(modelData.getKind() + ":" + modelData.getData());
            WithId entity = clazz.cast(mapper.readValue(modelData.getData(), clazz));
            Optional<String> id = entity.getId();
            String idVal;
            if (!id.isPresent()) {
                idVal = generatePK(cache);
                entity = entity.withId(idVal);
            } else {
                idVal = id.get();
            }
            cache.put(idVal, entity);
        } catch (Exception e) {
            IPaasServerException.launderThrowable(e);
        }
    }

    /**
     * Simple generator to mimic behavior in api-client project. When we start
     * hooking up the back-end systems we may need to query those for the PK
     *
     * @param entityMap
     * @return
     */
    public String generatePK(Cache<String, WithId> entityMap) {
        int counter = 1;
        while (true) {
            String pk = String.valueOf(entityMap.size() + counter++);
            if (!entityMap.containsKey(pk)) {
                return pk;
            }
        }
    }

    public Class<? extends WithId> getClass(String kind) {
        switch (kind.toLowerCase()) {
            case Connector.KIND:
                return Connector.class;
            case ConnectorGroup.KIND:
                return ConnectorGroup.class;
            case Connection.KIND:
                return Connection.class;
            case Environment.KIND:
                return Environment.class;
            case EnvironmentType.KIND:
                return EnvironmentType.class;
            case Integration.KIND:
                return Integration.class;
            case IntegrationConnectionStep.KIND:
                return IntegrationConnectionStep.class;
            case IntegrationPattern.KIND:
                return IntegrationPattern.class;
            case IntegrationPatternGroup.KIND:
                return IntegrationPatternGroup.class;
            case IntegrationRuntime.KIND:
                return IntegrationRuntime.class;
            case IntegrationTemplate.KIND:
                return IntegrationTemplate.class;
            case IntegrationTemplateConnectionStep.KIND:
                return IntegrationTemplateConnectionStep.class;
            case Organization.KIND:
                return Organization.class;
            case Permission.KIND:
                return Permission.class;
            case Role.KIND:
                return Role.class;
            case Step.KIND:
                return Step.class;
            case Tag.KIND:
                return Tag.class;
            case User.KIND:
                return User.class;
            default:
                break;
        }
        throw IPaasServerException.launderThrowable(new IllegalArgumentException("No matching class found for model " + kind));
    }

    @SuppressWarnings("unchecked")
    public <T extends WithId> ListResult<T> fetchAll(String kind, Function<ListResult<T>, ListResult<T>>... operators) {
        Cache<String, WithId> cache = caches.getCache(kind);

        //TODO: This is currently broken and needs to be properly addressed.
        //... until then just use the cache for pre-loaded data.
        if (cache.isEmpty()) {
            return (ListResult<T>) doWithDataAccessObject(kind, d -> d.fetchAll());
        }

        ListResult<T> result = new ListResult.Builder<T>()
            .items((Collection<T>) cache.values())
            .totalCount(cache.values().size())
            .build();

        for (Function<ListResult<T>, ListResult<T>> operator : operators) {
            result = operator.apply(result);
        }
        return result;
    }

    public <T extends WithId> T fetch(String kind, String id) {
        Map<String, WithId> cache = caches.getCache(kind);
        return (T) cache.computeIfAbsent(id, i -> doWithDataAccessObject(kind, d -> (T) d.fetch(i)));
    }

    public <T extends WithId> T create(T entity) {
        String kind = entity.getKind();
        Cache<String, WithId> cache = caches.getCache(kind);
        Optional<String> id = entity.getId();
        String idVal;
        if (!id.isPresent()) {
            idVal = generatePK(cache);
            entity = (T) entity.withId(idVal);
        } else {
            idVal = id.get();
            if (cache.keySet().contains(idVal)) {
                throw new EntityExistsException("There already exists a "
                    + kind + " with id " + idVal);
            }
        }

        cache.put(idVal, entity);
        broadcast("created", kind, idVal);
        return entity;
    }

    public void update(WithId entity) {
        String kind = entity.getKind();
        Map<String, WithId> cache = caches.getCache(kind);

        Optional<String> id = entity.getId();
        if (!id.isPresent()) {
            throw new EntityNotFoundException("Setting the id on the entity is required for updates");
        }

        String idVal = id.get();
        if (!cache.containsKey(idVal)) {
            throw new EntityNotFoundException("Can not find " + kind + " with id " + idVal);
        }

        doWithDataAccessObject(kind, d -> d.update(entity));
        cache.put(idVal, entity);
        broadcast("updated", kind, idVal);

        //TODO 1. properly merge the data ? + add data validation in the REST Resource
    }


    public boolean delete(String kind, String id) {
        Map<String, WithId> cache = caches.getCache(kind);
        if (id == null || id.equals(""))
            throw new EntityNotFoundException("Setting the id on the entity is required for updates");
        if (!cache.containsKey(id))
            throw new EntityNotFoundException("Can not find " + kind + " with id " + id);

        WithId entity = cache.get(id);
        if (entity != null && doWithDataAccessObject(kind, d -> d.delete(entity))) {
            cache.remove(id);
            broadcast("deleted", kind, id);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Map<Class, DataAccessObject> getDataAccessObjectMapping() {
        return dataAccessObjectMapping;
    }


    /**
     * Perform a simple action if a {@link DataAccessObject} for the specified kind exists.
     * This is just a way to avoid, duplivating the dao lookup and chekcs, which are going to change.
     * @param kind          The kind of the {@link DataAccessObject}.
     * @param function      The function to perfom on the {@link DataAccessObject}.
     * @param <O>           The return type.
     * @return              The outcome of the function.
     */
    private <O> O doWithDataAccessObject(String kind, Function<DataAccessObject, O> function) {
        DataAccessObject dataAccessObject = getDataAccessObject(getClass(kind));
        if (dataAccessObject != null) {
            return function.apply(dataAccessObject);
        }
        return null;
    }

    private void broadcast(String event, String type, String id) {
        if( eventBus !=null ) {
            eventBus.broadcast("change-event", ChangeEvent.of(event, type, id).toJson());
        }
    }

}
