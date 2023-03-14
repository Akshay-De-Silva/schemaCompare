/*
 * Copyright (c) 2022, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package schemacompare.models;                                                                   //changed package

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Client to store persist entities.
 *
 * @since 0.1.0
 */
public class Entity {

    private final List<EntityField> keys;
    private final String resourceName;

    private final String entityName;

    private final List<EntityField> fields;

    private Entity(String entityName, List<EntityField> keys,
                   String resourceName, List<EntityField> fields) {
        this.entityName = entityName;
        this.keys = Collections.unmodifiableList(keys);
        this.resourceName = resourceName;
        this.fields = Collections.unmodifiableList(fields);
    }

    public List<EntityField> getKeys() {
        return this.keys;
    }

    public String getResourceName() {
        return this.resourceName;
    }

    public String getEntityName() {
        return this.entityName;
    }

    public List<EntityField> getFields() {
        return this.fields;
    }

    public EntityField getFieldByName(String fieldName) {                                   //NEWLY ADDED
        for (EntityField field : fields) {
            if (field.getFieldName().equals(fieldName)) {
                return field;
            }
        }
        return null;
    }

    public static Entity.Builder newBuilder(String entityName) {
        return new Entity.Builder(entityName);
    }

    /**
     * Entity Definition.Builder.
     */
    public static class Builder {
        String entityName;
        String resourceName = null;
        List<EntityField> keys;

        List<EntityField> fieldList = null;

        private Builder(String entityName) {
            this.entityName = entityName;
        }

        public void setKeys(List<EntityField> keys) {
            this.keys = keys;
        }

        public void addField(EntityField field) {
            if (fieldList == null) {
                this.fieldList = new ArrayList<>();
            }
            fieldList.add(field);
        }

        public Entity build() {
            if (resourceName == null) {
                resourceName = entityName.toLowerCase(Locale.ENGLISH);
            }
            return new Entity(entityName, keys, resourceName, fieldList);
        }
    }
}
