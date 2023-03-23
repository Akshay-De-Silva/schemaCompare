package schemacompare;

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

        List<String> addedEntities = new ArrayList<>();
        List<String> removedEntities = new ArrayList<>();
        List<String> updatedEntities = new ArrayList<>();
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
                        updateEntity(updatedEntities, entity1);
                        addToMap(entity1, field1, removedFields, false, foreignKeyAction.NONE);
                    } else if(field1.getRelation().isOwner()) {
                        differences.add("Foreign key " + field1.getFieldName() + " has been removed from entity " + entity1.getEntityName());
                        updateEntity(updatedEntities, entity1);
                        addToMap(entity1, field1, removedForeignKeys, false, foreignKeyAction.REMOVE);
                    }
                    continue;
                }

                // Compare data types
                if (!field1.getFieldType().equals(field2.getFieldType())) {
                    differences.add("Data type of field " + field1.getFieldName() + " in entity " + entity1.getEntityName() + " has changed from " + field1.getFieldType() + " to " + field2.getFieldType());
                    updateEntity(updatedEntities, entity1);
                    addToMap(entity1, field2, changedFieldTypes, true, foreignKeyAction.NONE);
                }

                //Compare readonly fields
                if (entity1.getKeys().contains(field1) && !entity2.getKeys().contains(field2)) {
                    differences.add("Field " + field1.getFieldName() + " in entity " + entity1.getEntityName() + " is no longer a readonly field");
                    updateEntity(updatedEntities, entity1);
                    addToMap(entity1, field1, removedReadOnly, false, foreignKeyAction.NONE);

                } else if (!entity1.getKeys().contains(field1) && entity2.getKeys().contains(field2)) {
                    differences.add("Field " + field1.getFieldName() + " in entity " + entity1.getEntityName() + " is now a readonly field");
                    updateEntity(updatedEntities, entity1);
                    addToMap(entity1, field2, addedReadOnly, true, foreignKeyAction.NONE);
                }

            }

            // Check for added fields and for added foreign keys
            for (EntityField field2 : entity2.getFields()) {
                EntityField field1 = entity1.getFieldByName(field2.getFieldName());

                if (field1 == null) {
                    if(field2.getRelation() == null) {
                        if (entity2.getKeys().contains(field2)) {
                            differences.add("Field " + field2.getFieldName() + " of type " + field2.getFieldType() + " has been added to entity " + entity2.getEntityName() + " as a readonly field");
                            addToMap(entity2, field2, addedReadOnly, true, foreignKeyAction.NONE);

                        } else {
                            differences.add("Field " + field2.getFieldName() + " of type " + field2.getFieldType() + " has been added to entity " + entity2.getEntityName());
                        }
                        updateEntity(updatedEntities, entity2);
                        addToMap(entity2, field2, addedFields, true, foreignKeyAction.NONE);
                    } else if(field2.getRelation().isOwner()){
                        differences.add("Foreign key " + field2.getFieldName() + " of type " + field2.getFieldType() + " has been added to entity " + entity2.getEntityName());
                        updateEntity(updatedEntities, entity2);
                        addToMap(entity2, field2, addedForeignKeys, true, foreignKeyAction.ADD);
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
                            addToMap(entity2, field, addedReadOnly, true, foreignKeyAction.NONE);

                        } else {
                            differences.add("Field " + field.getFieldName() + " of type " + field.getFieldType() + " has been added to entity " + entity2.getEntityName());
                        }
                        updateEntity(updatedEntities, entity2);
                        addToMap(entity2, field, addedFields, true, foreignKeyAction.NONE);
                    }
                }
            }
        }

        System.out.println("Added entities: " + addedEntities + "\n");
        System.out.println("Removed entities: " + removedEntities + "\n");
        System.out.println("Updated entities: " + updatedEntities + "\n");
        System.out.println("Added fields: " + addedFields + "\n");
        System.out.println("Removed fields: " + removedFields + "\n");
        System.out.println("Changed field data types: " + changedFieldTypes + "\n");
        System.out.println("Added readonly fields: " + addedReadOnly + "\n");
        System.out.println("Removed readonly fields: " + removedReadOnly + "\n");
        System.out.println("Added foreign keys: " + addedForeignKeys + "\n");
        System.out.println("Removed foreign keys: " + removedForeignKeys + "\n");

        return differences;
    }

    // Update list of updated entities
    public static void updateEntity(List<String> updatedEntities, Entity entity) {
        if(!updatedEntities.contains(entity.getEntityName())) {
            updatedEntities.add(entity.getEntityName());
        }
    }

    // Add entity and field to map
    public static void addToMap(Entity entity, EntityField field, Map<String,List<Object>> map, boolean withType, foreignKeyAction action) {
        switch(action) {
            case ADD:
                String keyAddName = "FK_" + entity.getEntityName() + "_" + field.getRelation().getAssocEntity().getEntityName();
                if (!map.containsKey(entity.getEntityName())) {
                    List<Object> initialData = new ArrayList<>();
                    initialData.add(Arrays.toString(new Object[]{keyAddName, field.getRelation().getKeyColumns().get(0).getField(),
                                    field.getRelation().getKeyColumns().get(0).getType(), field.getRelation().getAssocEntity().getEntityName(),
                                    field.getRelation().getKeyColumns().get(0).getReference()}));
                    map.put(entity.getEntityName(), initialData);
                } else {
                    List<Object> existingData = map.get(entity.getEntityName());
                    existingData.add(Arrays.toString(new Object[]{keyAddName, field.getRelation().getKeyColumns().get(0).getField(),
                            field.getRelation().getKeyColumns().get(0).getType(), field.getRelation().getAssocEntity().getEntityName(),
                            field.getRelation().getKeyColumns().get(0).getReference()}));
                    map.put(entity.getEntityName(), existingData);
                }
                break;

            case REMOVE:
                String keyRemoveName = "FK_" + entity.getEntityName() + "_" + field.getRelation().getAssocEntity().getEntityName();

                if (!map.containsKey(entity.getEntityName())) {
                    List<Object> initialData = new ArrayList<>();
                    initialData.add(Arrays.toString(new Object[]{keyRemoveName}));
                    map.put(entity.getEntityName(), initialData);
                } else {
                    List<Object> existingData = map.get(entity.getEntityName());
                    existingData.add(Arrays.toString(new Object[]{keyRemoveName}));
                    map.put(entity.getEntityName(), existingData);
                }
                break;

            case NONE:
                if (withType) {
                    if (!map.containsKey(entity.getEntityName())) {
                        List<Object> initialData = new ArrayList<>();
                        initialData.add(Arrays.toString(new Object[]{field.getFieldName(), field.getFieldType()}));
                        map.put(entity.getEntityName(), initialData);
                    } else {
                        List<Object> existingData = map.get(entity.getEntityName());
                        existingData.add(Arrays.toString(new Object[]{field.getFieldName(), field.getFieldType()}));
                        map.put(entity.getEntityName(), existingData);
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

    public enum foreignKeyAction {
        ADD,
        REMOVE,
        NONE
    }

}
