// Copyright (c) 2022 WSO2 LLC. (https://www.wso2.com) All Rights Reserved.
//
// WSO2 LLC. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package schemacompare;

import io.ballerina.compiler.syntax.tree.*;
import io.ballerina.tools.text.TextDocuments;
import schemacompare.models.Entity;
import schemacompare.models.EntityField;
import schemacompare.models.Module;
import schemacompare.models.Relation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static io.ballerina.compiler.syntax.tree.SyntaxKind.QUALIFIED_NAME_REFERENCE;

public class schemaCompare {
    public static final String KEYWORD_BALLERINA = "ballerina";
    public static final String KEYWORD_PERSIST = "persist";
    public static final String COLON = ":";

    public static Module getEntities(Path schemaFile) throws Exception {
        Path schemaFilename = schemaFile.getFileName();
        String moduleName;
        if (schemaFilename != null) {
            moduleName = schemaFilename.toString().substring(0, schemaFilename.toString().lastIndexOf('.'));
        } else {
            throw new Exception("the model definition file name is invalid.");
        }
        Module.Builder moduleBuilder = Module.newBuilder(moduleName);

        try {
            SyntaxTree balSyntaxTree = SyntaxTree.from(TextDocuments.from(Files.readString(schemaFile)));
            populateEntities(moduleBuilder, balSyntaxTree);
            Module entityModule = moduleBuilder.build();
            inferRelationDetails(entityModule);
            return entityModule;
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }
    }

    public static void populateEntities(Module.Builder moduleBuilder, SyntaxTree balSyntaxTree) throws
            Exception {
        ModulePartNode rootNote = balSyntaxTree.rootNode();
        NodeList<ModuleMemberDeclarationNode> nodeList = rootNote.members();
        rootNote.imports().stream().filter(importNode -> importNode.orgName().isPresent() && importNode.orgName().get()
                        .orgName().text().equals(KEYWORD_BALLERINA) &&
                        importNode.moduleName().stream().anyMatch(node -> node.text().equals(KEYWORD_PERSIST)))
                .findFirst().orElseThrow(() -> new Exception(
                        "no `import ballerina/persist as _;` statement found.."));

        Entity.Builder entityBuilder;
        for (ModuleMemberDeclarationNode moduleNode : nodeList) {
            if (moduleNode.kind() != SyntaxKind.TYPE_DEFINITION) {
                continue;
            }
            TypeDefinitionNode typeDefinitionNode = (TypeDefinitionNode) moduleNode;
            entityBuilder = Entity.newBuilder(typeDefinitionNode.typeName().text().trim());

            List<EntityField> keyArray = new ArrayList<>();
            RecordTypeDescriptorNode recordDesc = (RecordTypeDescriptorNode) ((TypeDefinitionNode) moduleNode)
                    .typeDescriptor();
            for (Node node : recordDesc.fields()) {
                EntityField.Builder fieldBuilder;
                String qualifiedNamePrefix = null;
                if (node.kind() == SyntaxKind.RECORD_FIELD_WITH_DEFAULT_VALUE) {
                    RecordFieldWithDefaultValueNode fieldNode = (RecordFieldWithDefaultValueNode) node;

                    fieldBuilder = EntityField.newBuilder(fieldNode.fieldName().text().trim());
                    String fType;
                    TypeDescriptorNode type;
                    if (fieldNode.typeName().kind().equals(SyntaxKind.ARRAY_TYPE_DESC)) {
                        type = ((ArrayTypeDescriptorNode) fieldNode.typeName()).memberTypeDesc();
                        fieldBuilder.setArrayType(true);
                    } else {
                        type = (TypeDescriptorNode) fieldNode.typeName();
                    }
                    fType = getType(type, fieldNode.fieldName().text().trim());
                    qualifiedNamePrefix = getQualifiedModulePrefix(type);
                    fieldBuilder.setType(fType);
                    EntityField entityField = fieldBuilder.build();
                    entityBuilder.addField(entityField);
                    if (fieldNode.readonlyKeyword().isPresent()) {
                        keyArray.add(entityField);
                    }
                } else if (node.kind() == SyntaxKind.RECORD_FIELD) {
                    RecordFieldNode fieldNode = (RecordFieldNode) node;
                    fieldBuilder = EntityField.newBuilder(fieldNode.fieldName().text().trim());
                    String fType;
                    TypeDescriptorNode type;
                    if (fieldNode.typeName().kind().equals(SyntaxKind.ARRAY_TYPE_DESC)) {
                        type = ((ArrayTypeDescriptorNode) fieldNode.typeName()).memberTypeDesc();
                        fieldBuilder.setArrayType(true);
                    } else {
                        type = (TypeDescriptorNode) fieldNode.typeName();
                    }
                    fType = getType(type, fieldNode.fieldName().text().trim());
                    qualifiedNamePrefix = getQualifiedModulePrefix(type);
                    fieldBuilder.setType(fType);
                    fieldBuilder.setOptionalType(fieldNode.typeName().kind().equals(SyntaxKind.OPTIONAL_TYPE_DESC));
                    EntityField entityField = fieldBuilder.build();
                    entityBuilder.addField(entityField);
                    if (fieldNode.readonlyKeyword().isPresent()) {
                        keyArray.add(entityField);
                    }
                }
                if (qualifiedNamePrefix != null) {
                    moduleBuilder.addImportModulePrefix(qualifiedNamePrefix);
                }
            }
            entityBuilder.setKeys(keyArray);
            Entity entity = entityBuilder.build();
            moduleBuilder.addEntity(entity.getEntityName(), entity);
        }
    }

    public static void inferRelationDetails(Module entityModule) {
        Map<String, Entity> entityMap = entityModule.getEntityMap();
        for (Entity entity : entityMap.values()) {
            List<EntityField> fields = entity.getFields();
            fields.stream().filter(field -> entityMap.get(field.getFieldType()) != null)
                    .forEach(field -> {
                        String fieldType = field.getFieldType();
                        Entity assocEntity = entityMap.get(fieldType);
                        if (field.getRelation() == null) {
                            // this branch only handles one-to-many or many-to-many with no relation annotations
                            assocEntity.getFields().stream().filter(assocfield -> assocfield.getFieldType()
                                            .equals(entity.getEntityName()))
                                    .filter(assocfield -> assocfield.getRelation() == null).forEach(assocfield -> {
                                        // one-to-many or many-to-many with no relation annotations
                                        if (field.isArrayType() && assocfield.isArrayType()) {
                                            throw new RuntimeException("unsupported many to many relation between " +
                                                    entity.getEntityName() + " and " + assocEntity.getEntityName());
                                        }
                                        if (field.isArrayType() || field.isOptionalType()) {
                                            // one-to-many relation. associated entity is the owner.
                                            field.setRelation(computeRelation(entity, assocEntity, false));
                                            assocfield.setRelation(computeRelation(assocEntity, entity, true));
                                        } else {
                                            // one-to-many relation. entity is the owner.
                                            // one-to-one relation. entity is the owner.
                                            field.setRelation(computeRelation(entity, assocEntity, true));
                                            assocfield.setRelation(computeRelation(assocEntity, entity, false));
                                        }
                                    });
                        } else if (field.getRelation() != null && field.getRelation().isOwner()) {
                            field.getRelation().setRelationType(field.isArrayType() ?
                                    Relation.RelationType.MANY : Relation.RelationType.ONE);
                            field.getRelation().setAssocEntity(assocEntity);
                            List<Relation.Key> keyColumns = field.getRelation().getKeyColumns();
                            if (keyColumns == null || keyColumns.size() == 0) {
                                keyColumns = assocEntity.getKeys().stream().map(key ->
                                        new Relation.Key(assocEntity.getEntityName().toLowerCase(Locale.ENGLISH)
                                                + stripEscapeCharacter(key.getFieldName()).substring(0, 1)
                                                .toUpperCase(Locale.ENGLISH)
                                                + stripEscapeCharacter(key.getFieldName()).substring(1),
                                                key.getFieldName(), key.getFieldType())).collect(Collectors.toList());
                                field.getRelation().setKeyColumns(keyColumns);
                            }
                            List<String> references = field.getRelation().getReferences();
                            if (references == null || references.size() == 0) {
                                field.getRelation().setReferences(assocEntity.getKeys().stream()
                                        .map(EntityField::getFieldName)
                                        .collect(Collectors.toList()));
                            }

                            // create bidirectional mapping for associated entity
                            Relation.Builder assocRelBuilder = Relation.newBuilder();
                            assocRelBuilder.setOwner(false);
                            assocRelBuilder.setAssocEntity(entity);

                            List<Relation.Key> assockeyColumns = assocEntity.getKeys().stream().map(key ->
                                            new Relation.Key(key.getFieldName(),
                                                    assocEntity.getEntityName().toLowerCase(Locale.ENGLISH)
                                                            + stripEscapeCharacter(key.getFieldName()).substring(0, 1)
                                                            .toUpperCase(Locale.ENGLISH)
                                                            + stripEscapeCharacter(key.getFieldName()).substring(1),
                                                    key.getFieldType()))
                                    .collect(Collectors.toList());
                            assocRelBuilder.setKeys(assockeyColumns);
                            assocRelBuilder.setReferences(assockeyColumns.stream().map(Relation.Key::getReference)
                                    .collect(Collectors.toList()));
                            assocEntity.getFields().stream().filter(assocfield -> assocfield.getFieldType()
                                    .equals(entity.getEntityName())).forEach(
                                    assocField -> {
                                        assocRelBuilder.setRelationType(assocField.isArrayType() ?
                                                Relation.RelationType.MANY : Relation.RelationType.ONE);
                                        assocField.setRelation(assocRelBuilder.build());
                                    });
                        }
                    });
        }
    }

    private static String getType(TypeDescriptorNode typeDesc, String fieldName) throws Exception {
        switch (typeDesc.kind()) {
            case INT_TYPE_DESC:
            case BOOLEAN_TYPE_DESC:
            case DECIMAL_TYPE_DESC:
            case FLOAT_TYPE_DESC:
            case STRING_TYPE_DESC:
            case BYTE_TYPE_DESC:
                return ((BuiltinSimpleNameReferenceNode) typeDesc).name().text();
            case QUALIFIED_NAME_REFERENCE:
                QualifiedNameReferenceNode qualifiedName = (QualifiedNameReferenceNode) typeDesc;
                String modulePrefix = qualifiedName.modulePrefix().text();
                String identifier = qualifiedName.identifier().text();
                return modulePrefix + COLON + identifier;
            case SIMPLE_NAME_REFERENCE:
                return ((SimpleNameReferenceNode) typeDesc).name().text();
            case OPTIONAL_TYPE_DESC:
                return getType((TypeDescriptorNode) ((OptionalTypeDescriptorNode) typeDesc).typeDescriptor(),
                        fieldName);
            default:
                throw new Exception(String.format("unsupported data type found for the field `%s`", fieldName));
        }
    }

    private static String getQualifiedModulePrefix(TypeDescriptorNode typeDesc) {
        if (typeDesc.kind() == QUALIFIED_NAME_REFERENCE) {
            QualifiedNameReferenceNode qualifiedName = (QualifiedNameReferenceNode) typeDesc;
            return qualifiedName.modulePrefix().text();
        } else {
            return null;
        }
    }

    private static Relation computeRelation(Entity entity, Entity assocEntity, boolean isOwner) {
        Relation.Builder relBuilder = new Relation.Builder();
        relBuilder.setAssocEntity(assocEntity);
        if (isOwner) {
            List<Relation.Key> keyColumns = assocEntity.getKeys().stream().map(key ->
                    new Relation.Key(assocEntity.getEntityName().toLowerCase(Locale.ENGLISH)
                            + stripEscapeCharacter(key.getFieldName()).substring(0, 1).toUpperCase(Locale.ENGLISH)
                            + stripEscapeCharacter(key.getFieldName()).substring(1), key.getFieldName(),
                            key.getFieldType())).collect(Collectors.toList());
            relBuilder.setOwner(true);
            relBuilder.setRelationType(Relation.RelationType.ONE);
            relBuilder.setKeys(keyColumns);
            relBuilder.setReferences(assocEntity.getKeys().stream().map(EntityField::getFieldName)
                    .collect(Collectors.toList()));
        } else {
            List<Relation.Key> keyColumns = entity.getKeys().stream().map(key ->
                            new Relation.Key(key.getFieldName(),
                                    entity.getEntityName().toLowerCase(Locale.ENGLISH)
                                            + stripEscapeCharacter(key.getFieldName()).substring(0, 1).toUpperCase(Locale.ENGLISH)
                                            + stripEscapeCharacter(key.getFieldName()).substring(1), key.getFieldType()))
                    .collect(Collectors.toList());
            relBuilder.setOwner(false);
            relBuilder.setRelationType(Relation.RelationType.MANY);
            relBuilder.setKeys(keyColumns);
            relBuilder.setReferences(keyColumns.stream().map(Relation.Key::getReference).collect(Collectors.toList()));
        }
        return relBuilder.build();
    }

    private static String stripEscapeCharacter(String fieldName) {
        return fieldName.startsWith("'") ? fieldName.substring(1) : fieldName;
    }

}
