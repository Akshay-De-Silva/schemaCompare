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

    public static List<String> findDifferences(Module module1, Module module2) {
        List<String> differences = new ArrayList<>();
        List<String> queries = new ArrayList<>();
        HashMap<String,List<Object>> primaryKeys = new HashMap<>();

        List<String> addedEntities = new ArrayList<>();
        List<String> removedEntities = new ArrayList<>();
        HashMap<String,List<Object>> addedFields = new HashMap<>();
        HashMap<String,List<Object>> removedFields = new HashMap<>();
        HashMap<String,List<Object>> changedFieldTypes = new HashMap<>();
        HashMap<String,List<Object>> addedReadOnly = new HashMap<>();
        HashMap<String,List<Object>> removedReadOnly = new HashMap<>();
        HashMap<String,List<Object>> addedForeignKeys = new HashMap<>();
        HashMap<String,List<Object>> removedForeignKeys = new HashMap<>();

        // Compare entities in module1 and module2
        for (Entity entity1 : module1.getEntityMap().values()) {
            Entity entity2 = module2.getEntityMap().get(entity1.getEntityName());

            // Check if entity2 exists
            if (entity2 == null) {
                differences.add("Entity " + entity1.getEntityName() + " has been removed");
                removedEntities.add(entity1.getEntityName());
                continue;
            }

            // Compare fields in entity1 and entity2
            for (EntityField field1 : entity1.getFields()) {
                EntityField field2 = entity2.getFieldByName(field1.getFieldName());

                // Check if field2 exists and if foreign key was removed
                if (field2 == null) {
                    if(field1.getRelation() == null) {
                        differences.add("Field " + field1.getFieldName() + " has been removed from entity " + entity1.getEntityName());
                        addToMap(entity1, field1, removedFields, false, false, foreignKeyAction.NONE);
                    } else if(field1.getRelation().isOwner()) {
                        differences.add("Foreign key " + field1.getFieldName() + " has been removed from entity " + entity1.getEntityName());
                        addToMap(entity1, field1, removedForeignKeys, false, false, foreignKeyAction.REMOVE);
                    }
                    continue;
                }

                // Compare data types
                if (!field1.getFieldType().equals(field2.getFieldType())) {
                    differences.add("Data type of field " + field1.getFieldName() + " in entity " + entity1.getEntityName() + " has changed from " + field1.getFieldType() + " to " + field2.getFieldType());
                    addToMap(entity1, field2, changedFieldTypes, true, false, foreignKeyAction.NONE);
                }

                //Compare readonly fields
                if (entity1.getKeys().contains(field1) && !entity2.getKeys().contains(field2)) {
                    differences.add("Field " + field1.getFieldName() + " in entity " + entity1.getEntityName() + " is no longer a readonly field");
                    addToMap(entity1, field1, removedReadOnly, false, false, foreignKeyAction.NONE);

                } else if (!entity1.getKeys().contains(field1) && entity2.getKeys().contains(field2)) {
                    differences.add("Field " + field1.getFieldName() + " in entity " + entity1.getEntityName() + " is now a readonly field");
                    addToMap(entity1, field2, addedReadOnly, false, false, foreignKeyAction.NONE);
                }

            }

            // Check for added fields and for added foreign keys
            for (EntityField field2 : entity2.getFields()) {
                EntityField field1 = entity1.getFieldByName(field2.getFieldName());

                if (field1 == null) {
                    if(field2.getRelation() == null) {
                        if (entity2.getKeys().contains(field2)) {
                            differences.add("Field " + field2.getFieldName() + " of type " + field2.getFieldType() + " has been added to entity " + entity2.getEntityName() + " as a readonly field");
                            addToMap(entity2, field2, addedReadOnly, false, false, foreignKeyAction.NONE);

                        } else {
                            differences.add("Field " + field2.getFieldName() + " of type " + field2.getFieldType() + " has been added to entity " + entity2.getEntityName());
                        }
                        addToMap(entity2, field2, addedFields, true, false, foreignKeyAction.NONE);
                    } else if(field2.getRelation().isOwner()){
                        differences.add("Field " + field2.getRelation().getKeyColumns().get(0).getField() + " of type " + field2.getRelation().getKeyColumns().get(0).getType() + " has been added to entity " + entity2.getEntityName() + " as a foreign key");
                        addToMap(entity2, field2, addedFields, true, true, foreignKeyAction.NONE);

                        differences.add("Foreign key " + field2.getFieldName() + " of type " + field2.getFieldType() + " has been added to entity " + entity2.getEntityName());
                        addToMap(entity2, field2, addedForeignKeys, true, false, foreignKeyAction.ADD);
                    }
                }
            }
        }

        // Check for added entities
        for (Entity entity2 : module2.getEntityMap().values()) {
            Entity entity1 = module1.getEntityMap().get(entity2.getEntityName());

            if (entity1 == null) {
                differences.add("Entity " + entity2.getEntityName() + " has been added");
                addedEntities.add(entity2.getEntityName());
                for (EntityField field : entity2.getFields()) {
                    if(field.getRelation() == null) {
                        if(entity2.getKeys().contains(field)) {
                            differences.add("Field " + field.getFieldName() + " of type " + field.getFieldType() + " has been added to entity " + entity2.getEntityName() + " as a readonly field");
                            addToMap(entity2, field, addedReadOnly, true, false, foreignKeyAction.NONE);

                        } else {
                            differences.add("Field " + field.getFieldName() + " of type " + field.getFieldType() + " has been added to entity " + entity2.getEntityName());
                            addToMap(entity2, field, addedFields, true, false, foreignKeyAction.NONE);
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

//        System.out.println("Added entities: " + addedEntities + "\n");
//        System.out.println("Removed entities: " + removedEntities + "\n");
//        System.out.println("Added fields: " + addedFields + "\n");
//        System.out.println("Removed fields: " + removedFields + "\n");
//        System.out.println("Changed field data types: " + changedFieldTypes + "\n");
//        System.out.println("Added readonly fields: " + addedReadOnly + "\n");
//        System.out.println("Removed readonly fields: " + removedReadOnly + "\n");
//        System.out.println("Added foreign keys: " + addedForeignKeys + "\n");
//        System.out.println("Removed foreign keys: " + removedForeignKeys + "\n");

        // Convert differences to queries (ordered)
        convertListToQuery(queryTypes.ADD_TABLE, addedEntities, queries, primaryKeys);
        convertMapToQuery(queryTypes.ADD_FIELD, addedFields, queries);
        convertMapToQuery(queryTypes.REMOVE_FOREIGN_KEY, removedForeignKeys, queries);
        convertMapToQuery(queryTypes.REMOVE_READONLY, removedReadOnly, queries);
        convertMapToQuery(queryTypes.ADD_READONLY, addedReadOnly, queries);
        convertMapToQuery(queryTypes.ADD_FOREIGN_KEY, addedForeignKeys, queries);
        convertMapToQuery(queryTypes.REMOVE_FIELD, removedFields, queries);
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

    // Add entity and field to map
    public static void addToMap(Entity entity, EntityField field, Map<String,List<Object>> map, boolean withType, boolean foreignKey, foreignKeyAction action) {
        switch(action) {
            case ADD:
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
                break;

            case REMOVE:
                String RemoveKeyName = String.format("FK_%s_%s", entity.getEntityName(), field.getRelation().getAssocEntity().getEntityName());

                if (!map.containsKey(entity.getEntityName())) {
                    List<Object> initialData = new ArrayList<>();
                    initialData.add(Arrays.toString(new Object[]{RemoveKeyName}));
                    map.put(entity.getEntityName(), initialData);
                } else {
                    List<Object> existingData = map.get(entity.getEntityName());
                    existingData.add(Arrays.toString(new Object[]{RemoveKeyName}));
                    map.put(entity.getEntityName(), existingData);
                }
                break;

            case NONE:
                if (withType) {
                    if (foreignKey) {
                        if (!map.containsKey(entity.getEntityName())) {
                            List<Object> initialData = new ArrayList<>();
                            initialData.add(Arrays.toString(new Object[]{field.getRelation().getKeyColumns().get(0).getField(), field.getRelation().getKeyColumns().get(0).getType()}));
                            map.put(entity.getEntityName(), initialData);
                        } else {
                            List<Object> existingData = map.get(entity.getEntityName());
                            existingData.add(Arrays.toString(new Object[]{field.getRelation().getKeyColumns().get(0).getField(), field.getRelation().getKeyColumns().get(0).getType()}));
                            map.put(entity.getEntityName(), existingData);
                        }
                    } else {
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
                } else {
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
                break;
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

    // Convert map to a MySQL query
    public static void convertMapToQuery(queryTypes type, Map<String,List<Object>> map, List<String> queries) {
        switch(type) {
            case ADD_FIELD:
                for (String entity : map.keySet()) {
                    for (Object field : map.get(entity)) {
                        String[] fieldData = field.toString().split(",");
                        queries.add("ALTER TABLE " + entity + " ADD COLUMN " + fieldData[0].substring(1) + " " + getDataType(fieldData[1].substring(1,fieldData[1].length()-1)) + ";");
                    }
                }
                break;

            case REMOVE_FIELD:
                for (String entity : map.keySet()) {
                    for (Object field : map.get(entity)) {
                        queries.add("ALTER TABLE " + entity + " DROP COLUMN " + field.toString().substring(1,field.toString().length()-1) + ";");
                    }
                }
                break;

            case CHANGE_TYPE:
                for (String entity : map.keySet()) {
                    for (Object field : map.get(entity)) {
                        String[] fieldData = field.toString().split(",");
                        queries.add("ALTER TABLE " + entity + " MODIFY COLUMN " + fieldData[0].substring(1) + " " + getDataType(fieldData[1].substring(1,fieldData[1].length()-1)) + ";");
                    }
                }
                break;

            case ADD_READONLY:
                for (String entity : map.keySet()) {
                    for (Object field : map.get(entity)) {
                        queries.add("ALTER TABLE " + entity + " ADD PRIMARY KEY (" + field.toString().substring(1,field.toString().length()-1) + ");");
                    }
                }
                break;

            case REMOVE_READONLY:
                for (String entity : map.keySet()) {
                    queries.add("ALTER TABLE " + entity + " DROP PRIMARY KEY;");
                }
                break;

            case ADD_FOREIGN_KEY:
                for (String entity : map.keySet()) {
                    for (Object field : map.get(entity)) {
                        String[] fieldData = field.toString().split(",");
                        queries.add("ALTER TABLE " + entity + " ADD CONSTRAINT " + fieldData[0].substring(1) + " FOREIGN KEY (" + fieldData[1].substring(1) + ") REFERENCES" + fieldData[2] + "(" + fieldData[3].substring(1, fieldData[3].length()-1) + ");");
                    }
                }
                break;

            case REMOVE_FOREIGN_KEY:
                for (String entity : map.keySet()) {
                    for (Object field : map.get(entity)) {
                        String[] fieldData = field.toString().split(",");
                        queries.add("ALTER TABLE " + entity + " DROP FOREIGN KEY " + fieldData[0].substring(1,fieldData[0].length()-1) + ";");
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

    // Types of actions for foreign keys
    public enum foreignKeyAction {
        ADD,
        REMOVE,
        NONE
    }

}
