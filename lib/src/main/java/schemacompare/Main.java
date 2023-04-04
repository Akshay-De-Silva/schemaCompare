package schemacompare;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import schemacompare.models.Entity;
import schemacompare.models.EntityField;
import schemacompare.models.Module;

public class Main {
    public static void main(String[] args) {
        Path path1 = Paths.get("/home/wso2/IdeaProjects/schemaCompare/lib/src/test/resources/medicalEntities.bal");
        Path path2 = Paths.get("/home/wso2/IdeaProjects/schemaCompare/lib/src/test/resources/medicalEntitiesNew.bal");
        Path path3 = Paths.get("/home/wso2/IdeaProjects/schemaCompare/lib/src/test/resources/test.bal");
        Path path4 = Paths.get("/home/wso2/IdeaProjects/schemaCompare/lib/src/test/resources/testNew.bal");

        try {
            Module module1 = schemaCompare.getEntities(path1);
            Module module2 = schemaCompare.getEntities(path2);
            Module module3 = schemaCompare.getEntities(path3);
            Module module4 = schemaCompare.getEntities(path4);

            List<String> differences = findDifferences(module1, module2);
            System.out.println("Detailed list of differences: ");
            System.out.println(differences);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<String> findDifferences(Module currentModel, Module newModel) {
        List<String> differences = new ArrayList<>();
        List<String> queries = new ArrayList<>();
        HashMap<String,List<Object>> primaryKeys = new HashMap<>();

        List<String> addedEntities = new ArrayList<>();
        List<String> removedEntities = new ArrayList<>();
        HashMap<String,List<Object>> addedFields = new HashMap<>();
        HashMap<String,List<String>> removedFields = new HashMap<>();
        HashMap<String,List<Object>> changedFieldTypes = new HashMap<>();
        HashMap<String,List<Object>> addedReadOnly = new HashMap<>();
        HashMap<String,List<String>> removedReadOnly = new HashMap<>();
        HashMap<String,List<Object>> addedForeignKeys = new HashMap<>();
        HashMap<String,List<String>> removedForeignKeys = new HashMap<>();

        // Compare entities in currentModel and newModel
        for (Entity currentModelEntity : currentModel.getEntityMap().values()) {
            Entity newModelEntity = newModel.getEntityMap().get(currentModelEntity.getEntityName());

            // Check if newModelEntity exists
            if (newModelEntity == null) {
                differences.add("Entity " + currentModelEntity.getEntityName() + " has been removed");
                removedEntities.add(currentModelEntity.getEntityName());
                continue;
            }

            // Compare fields in currentModelEntity and newModelEntity
            for (EntityField currentModelField : currentModelEntity.getFields()) {
                EntityField newModelField = newModelEntity.getFieldByName(currentModelField.getFieldName());

                // Check if newModelField exists and if foreign key was removed
                if (newModelField == null) {
                    if(currentModelField.getRelation() == null) {
                        differences.add("Field " + currentModelField.getFieldName() + " has been removed from entity " + currentModelEntity.getEntityName());
                        addToMapNoTypeString(currentModelEntity, currentModelField, removedFields);
                    } else if(currentModelField.getRelation().isOwner()) {
                        differences.add("Foreign key " + currentModelField.getFieldName() + " has been removed from entity " + currentModelEntity.getEntityName());
                        addToMapRemoveForeignKey(currentModelEntity, currentModelField, removedForeignKeys);
                    }
                    continue;
                }

                // Compare data types
                if (!currentModelField.getFieldType().equals(newModelField.getFieldType())) {
                    differences.add("Data type of field " + currentModelField.getFieldName() + " in entity " + currentModelEntity.getEntityName() + " has changed from " + currentModelField.getFieldType() + " to " + newModelField.getFieldType());
                    addToMapWithType(currentModelEntity, newModelField, changedFieldTypes);
                }

                //Compare readonly fields
                if (currentModelEntity.getKeys().contains(currentModelField) && !newModelEntity.getKeys().contains(newModelField)) {
                    differences.add("Field " + currentModelField.getFieldName() + " in entity " + currentModelEntity.getEntityName() + " is no longer a readonly field");
                    addToMapNoTypeString(currentModelEntity, currentModelField, removedReadOnly);

                } else if (!currentModelEntity.getKeys().contains(currentModelField) && newModelEntity.getKeys().contains(newModelField)) {
                    differences.add("Field " + currentModelField.getFieldName() + " in entity " + currentModelEntity.getEntityName() + " is now a readonly field");
                    addToMapNoTypeObject(currentModelEntity, newModelField, addedReadOnly);
                }

            }

            // Check for added fields and for added foreign keys
            for (EntityField newModelField : newModelEntity.getFields()) {
                EntityField currentModelField = currentModelEntity.getFieldByName(newModelField.getFieldName());

                if (currentModelField == null) {
                    if(newModelField.getRelation() == null) {
                        if (newModelEntity.getKeys().contains(newModelField)) {
                            differences.add("Field " + newModelField.getFieldName() + " of type " + newModelField.getFieldType() + " has been added to entity " + newModelEntity.getEntityName() + " as a readonly field");
                            addToMapNoTypeObject(newModelEntity, newModelField, addedReadOnly);

                        } else {
                            differences.add("Field " + newModelField.getFieldName() + " of type " + newModelField.getFieldType() + " has been added to entity " + newModelEntity.getEntityName());
                        }
                        addToMapWithType(newModelEntity, newModelField, addedFields);
                    } else if(newModelField.getRelation().isOwner()){
                        differences.add("Field " + newModelField.getRelation().getKeyColumns().get(0).getField() + " of type " + newModelField.getRelation().getKeyColumns().get(0).getType() + " has been added to entity " + newModelEntity.getEntityName() + " as a foreign key");
                        addToMapNewEntityFK(newModelEntity, newModelField, addedFields);

                        differences.add("Foreign key " + newModelField.getFieldName() + " of type " + newModelField.getFieldType() + " has been added to entity " + newModelEntity.getEntityName());
                        addToMapAddForeignKey(newModelEntity, newModelField, addedForeignKeys);
                    }
                }
            }
        }

        // Check for added entities
        for (Entity newModelEntity : newModel.getEntityMap().values()) {
            Entity currentModelEntity = currentModel.getEntityMap().get(newModelEntity.getEntityName());

            if (currentModelEntity == null) {
                differences.add("Entity " + newModelEntity.getEntityName() + " has been added");
                addedEntities.add(newModelEntity.getEntityName());
                for (EntityField field : newModelEntity.getFields()) {
                    if(field.getRelation() == null) {
                        if(newModelEntity.getKeys().contains(field)) {
                            differences.add("Field " + field.getFieldName() + " of type " + field.getFieldType() + " has been added to entity " + newModelEntity.getEntityName() + " as a readonly field");
                            addToMapWithType(newModelEntity, field, addedReadOnly);

                        } else {
                            differences.add("Field " + field.getFieldName() + " of type " + field.getFieldType() + " has been added to entity " + newModelEntity.getEntityName());
                            addToMapWithType(newModelEntity, field, addedFields);
                        }
                    }
                }
            }
        }

        //Save new table primary keys and remove them from addedReadOnly
        for (String entity : addedEntities) {
            primaryKeys.put(entity, addedReadOnly.get(entity));
            addedReadOnly.remove(entity);
        }

        System.out.println("Added entities: " + addedEntities + "\n");
        System.out.println("Removed entities: " + removedEntities + "\n");
        System.out.println("Added fields: " + addedFields + "\n");
        System.out.println("Removed fields: " + removedFields + "\n");
        System.out.println("Changed field data types: " + changedFieldTypes + "\n");
        System.out.println("Added readonly fields: " + addedReadOnly + "\n");
        System.out.println("Removed readonly fields: " + removedReadOnly + "\n");
        System.out.println("Added foreign keys: " + addedForeignKeys + "\n");
        System.out.println("Removed foreign keys: " + removedForeignKeys + "\n");

        // Convert differences to queries (ordered)
        convertListToQuery(queryTypes.ADD_TABLE, addedEntities, queries, primaryKeys);
        convertMapToQuery(queryTypes.ADD_FIELD, addedFields, queries);
        convertMapListToQuery(queryTypes.REMOVE_FOREIGN_KEY, removedForeignKeys, queries);
        convertMapListToQuery(queryTypes.REMOVE_READONLY, removedReadOnly, queries);
        convertMapToQuery(queryTypes.ADD_READONLY, addedReadOnly, queries);
        convertMapToQuery(queryTypes.ADD_FOREIGN_KEY, addedForeignKeys, queries);
        convertMapListToQuery(queryTypes.REMOVE_FIELD, removedFields, queries);
        convertListToQuery(queryTypes.REMOVE_TABLE, removedEntities, queries, primaryKeys);
        convertMapToQuery(queryTypes.CHANGE_TYPE, changedFieldTypes, queries);

        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter("migrate.sql"));

            for (String query : queries) {
                writer.write(query);
                writer.newLine();
            }
            writer.close();

            System.out.println("Successfully migrated");
        } catch (IOException e) {
            System.out.println("An error occurred while writing to file: " + e.getMessage());
        }

        return differences;
    }

    public static void addToMapAddForeignKey (Entity entity, EntityField field, Map<String,List<Object>> map) {
        String AddKeyName = String.format("FK_%s_%s", entity.getEntityName(), field.getRelation().getAssocEntity().getEntityName());

        if (!map.containsKey(entity.getEntityName())) {
            List<Object> initialData = new ArrayList<>();
            initialData.add(Arrays.toString(new Object[]{AddKeyName, field.getRelation().getKeyColumns().get(0).getField(), field.getRelation().getAssocEntity().getEntityName(),
                    field.getRelation().getKeyColumns().get(0).getReference()}));
            map.put(entity.getEntityName(), initialData);
        } else {
            List<Object> existingData = map.get(entity.getEntityName());
            existingData.add(Arrays.toString(new Object[]{AddKeyName, field.getRelation().getKeyColumns().get(0).getField(), field.getRelation().getAssocEntity().getEntityName(),
                    field.getRelation().getKeyColumns().get(0).getReference()}));
            map.put(entity.getEntityName(), existingData);
        }
    }

    public static void addToMapRemoveForeignKey (Entity entity, EntityField field, Map<String,List<String>> map) {
        String RemoveKeyName = String.format("FK_%s_%s", entity.getEntityName(), field.getRelation().getAssocEntity().getEntityName());

        if (!map.containsKey(entity.getEntityName())) {
            List<String> initialData = new ArrayList<>();
            initialData.add(RemoveKeyName);
            map.put(entity.getEntityName(), initialData);
        } else {
            List<String> existingData = map.get(entity.getEntityName());
            existingData.add(RemoveKeyName);
            map.put(entity.getEntityName(), existingData);
        }
    }

    public static void addToMapNoTypeString (Entity entity, EntityField field, Map<String,List<String>> map) {
        if (!map.containsKey(entity.getEntityName())) {
            List<String> initialData = new ArrayList<>();
            initialData.add(field.getFieldName());
            map.put(entity.getEntityName(), initialData);
        } else {
            List<String> existingData = map.get(entity.getEntityName());
            existingData.add(field.getFieldName());
            map.put(entity.getEntityName(), existingData);
        }
    }

    public static void addToMapNoTypeObject (Entity entity, EntityField field, Map<String,List<Object>> map) {
        if (!map.containsKey(entity.getEntityName())) {
            List<Object> initialData = new ArrayList<>();
            initialData.add(Arrays.toString(new Object[]{field.getFieldName()}));
            map.put(entity.getEntityName(), initialData);
        } else {
            List<Object> existingData = map.get(entity.getEntityName());
            existingData.add(Arrays.toString(new Object[]{field.getFieldName()}));
            map.put(entity.getEntityName(), existingData);
        }
    }

    public static void addToMapWithType (Entity entity, EntityField field, Map<String,List<Object>> map) {
        if (!map.containsKey(entity.getEntityName())) {
            List<Object> initialData = new ArrayList<>();
            initialData.add(Arrays.toString(new Object[]{field.getFieldName(), field.getFieldType()}));
            map.put(entity.getEntityName(), initialData);
        } else {
            List<Object> existingData = map.get(entity.getEntityName());
            existingData.add(Arrays.toString(new Object[]{field.getFieldName(), field.getFieldType()}));
            map.put(entity.getEntityName(), existingData);
        }
    }

    public static void addToMapNewEntityFK (Entity entity, EntityField field, Map<String,List<Object>> map) {
        if (!map.containsKey(entity.getEntityName())) {
            List<Object> initialData = new ArrayList<>();
            initialData.add(Arrays.toString(new Object[]{field.getRelation().getKeyColumns().get(0).getField(), field.getRelation().getKeyColumns().get(0).getType()}));
            map.put(entity.getEntityName(), initialData);
        } else {
            List<Object> existingData = map.get(entity.getEntityName());
            existingData.add(Arrays.toString(new Object[]{field.getRelation().getKeyColumns().get(0).getField(), field.getRelation().getKeyColumns().get(0).getType()}));
            map.put(entity.getEntityName(), existingData);
        }
    }

    // Convert list to a MySQL query
    public static void convertListToQuery(queryTypes type, List<String> entities, List<String> queries, HashMap<String, List<Object>> primaryKeys) {
        switch(type) {
            case ADD_TABLE:
                for (String entity : entities) {
                    String[] primaryKeyData = primaryKeys.get(entity).toString().split(",");
                    queries.add("CREATE TABLE " + entity + " (" + primaryKeyData[0].substring(2) + " " + getDataType(primaryKeyData[1].substring(1, primaryKeyData[1].length()-2)) + " PRIMARY KEY);");
                }
                break;

            case REMOVE_TABLE:
                for (String entity : entities) {
                    queries.add("DROP TABLE " + entity + ";");
                }
                break;
        }
    }

    //Convert map of String lists to a MySQL query
    public static void convertMapListToQuery(queryTypes type, Map<String,List<String>> map, List<String> queries) {
        switch(type) {
            case REMOVE_FIELD:
                for (String entity : map.keySet()) {
                    for (String field : map.get(entity)) {
                        String removeFieldTemplate = "ALTER TABLE %s DROP COLUMN %s;";

                        queries.add(String.format(removeFieldTemplate, entity, field));
                    }
                }
                break;

            case REMOVE_READONLY:
                for (String entity : map.keySet()) {
                    String removeReadOnlyTemplate = "ALTER TABLE %s DROP PRIMARY KEY;";

                    queries.add(String.format(removeReadOnlyTemplate, entity));
                }
                break;

            case REMOVE_FOREIGN_KEY:
                for (String entity : map.keySet()) {
                    for (String field : map.get(entity)) {
                        String[] fieldData = field.split(",");

                        String foreign_key_name = fieldData[0];
                        String removeForeignKeyTemplate = "ALTER TABLE %s DROP FOREIGN KEY %s;";

                        queries.add(String.format(removeForeignKeyTemplate, entity, foreign_key_name));
                    }
                }
                break;
        }
    }

    // Convert map of Object lists to a MySQL query
    public static void convertMapToQuery(queryTypes type, Map<String,List<Object>> map, List<String> queries) {
        switch(type) {
            case ADD_FIELD:
                for (String entity : map.keySet()) {
                    for (Object field : map.get(entity)) {
                        String[] fieldData = field.toString().split(",");

                        String fieldName = fieldData[0].substring(1);
                        String fieldType = getDataType(fieldData[1].substring(1,fieldData[1].length()-1));
                        String addFieldTemplate = "ALTER TABLE %s ADD COLUMN %s %s;";

                        queries.add(String.format(addFieldTemplate, entity, fieldName, fieldType));
                    }
                }
                break;

            case CHANGE_TYPE:
                for (String entity : map.keySet()) {
                    for (Object field : map.get(entity)) {
                        String[] fieldData = field.toString().split(",");

                        String fieldName = fieldData[0].substring(1);
                        String fieldType = getDataType(fieldData[1].substring(1,fieldData[1].length()-1));
                        String changeTypeTemplate = "ALTER TABLE %s MODIFY COLUMN %s %s;";

                        queries.add(String.format(changeTypeTemplate, entity, fieldName, fieldType));
                    }
                }
                break;

            case ADD_READONLY:
                for (String entity : map.keySet()) {
                    for (Object field : map.get(entity)) {
                        String primaryKey = field.toString().substring(1,field.toString().length()-1);
                        String addReadOnlyTemplate = "ALTER TABLE %s ADD PRIMARY KEY (%s);";

                        queries.add(String.format(addReadOnlyTemplate, entity, primaryKey));
                    }
                }
                break;

            case ADD_FOREIGN_KEY:
                for (String entity : map.keySet()) {
                    for (Object field : map.get(entity)) {
                        String[] fieldData = field.toString().split(",");

                        String foreign_key_name = fieldData[0].substring(1);
                        String child_column_name = fieldData[1].substring(1);
                        String parent_table_name = fieldData[2].substring(1);
                        String parent_column_name = fieldData[3].substring(1, fieldData[3].length()-1);
                        String addForeignKeyTemplate = "ALTER TABLE %s ADD CONSTRAINT %s FOREIGN KEY (%s) REFERENCES %s(%s);";

                        queries.add(String.format(addForeignKeyTemplate, entity, foreign_key_name, child_column_name, parent_table_name, parent_column_name));
                    }
                }
                break;
        }
    }

    // Convert Java data type to MySQL data type
    public static String getDataType(String dataType) {
        String resultType;

        switch (dataType) {
            case "string":
                resultType = "VARCHAR(191)";
                break;

            case "int":
                resultType = "INT";
                break;

            case "long":
                resultType = "BIGINT";
                break;

            case "float":
                resultType = "FLOAT";
                break;

            case "double":
                resultType = "DOUBLE";
                break;

            case "boolean":
                resultType = "TINYINT(1)";
                break;

            case "Date":
            case "LocalDate":
                resultType = "DATE";
                break;

            case "LocalTime":
                resultType = "TIME";
                break;

            case "LocalDateTime":
                resultType = "DATETIME";
                break;

            case "Blob":
            case "byte[]":
                resultType = "BLOB";
                break;

            case "Clob":
                resultType = "CLOB";
                break;

            default:
                resultType = "ERROR: VARIABLE NOT FOUND";
                break;
        }

        return resultType;

    }

    // Types of MySQL queries
    public enum queryTypes {
        ADD_TABLE,
        REMOVE_TABLE,
        ADD_FIELD,
        REMOVE_FIELD,
        CHANGE_TYPE,
        ADD_READONLY,
        REMOVE_READONLY,
        ADD_FOREIGN_KEY,
        REMOVE_FOREIGN_KEY
    }

}
