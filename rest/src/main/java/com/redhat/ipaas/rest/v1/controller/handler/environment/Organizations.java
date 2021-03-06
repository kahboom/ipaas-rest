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
package com.redhat.ipaas.rest.v1.controller.handler.environment;

import com.redhat.ipaas.rest.v1.controller.handler.BaseHandler;
import com.redhat.ipaas.rest.v1.controller.operations.Getter;
import com.redhat.ipaas.rest.v1.controller.operations.Lister;
import com.redhat.ipaas.rest.v1.model.environment.Organization;
import org.springframework.stereotype.Component;

import javax.ws.rs.Path;

@Path("/organizations")
@Component
public class Organizations extends BaseHandler implements Lister<Organization>, Getter<Organization> {

    @Override
    public Class<Organization> resourceClass() {
        return Organization.class;
    }

    @Override
    public String resourceKind() {
        return Organization.KIND;
    }

}
