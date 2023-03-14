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

        try {
            Module module1 = schemaCompare.getEntities(path1);
            Module module2 = schemaCompare.getEntities(path2);

            List<String> differences = findDifferences(module1, module2);
            System.out.println("Detailed list of differences: ");
            System.out.println(differences);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<String> findDifferences(Module module1, Module module2) {
        List<String> differences = new ArrayList<>();

        List<String> addedEntities = new ArrayList<>(); // <entityName>
        List<String> removedEntities = new ArrayList<>(); // <entityName>
        HashMap<String,String> addedFields = new HashMap<>(); // <fieldName, [entityName, type]>
        HashMap<String,String> removedFields = new HashMap<>(); // <fieldName, entityName>
        HashMap<String,String> changedFieldTypes = new HashMap<>(); // <fieldName, [entityName, newType]>

        //List<String> changedKeys = new ArrayList<>();

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

                // Check if field2 exists
                if (field2 == null) {
                    differences.add("Field " + field1.getFieldName() + " has been removed from entity " + entity1.getEntityName());
                    removedFields.put(field1.getFieldName(), entity1.getEntityName());
                    continue;
                }

                // Compare data types
                if (!field1.getFieldType().equals(field2.getFieldType())) {
                    differences.add("Data type of field " + field1.getFieldName() + " in entity " + entity1.getEntityName() + " has changed from " + field1.getFieldType() + " to " + field2.getFieldType());
                    changedFieldTypes.put(field1.getFieldName(), Arrays.toString(new String[]{entity1.getEntityName(), field2.getFieldType()}));
                }

                // Compare readonly flag
//                if (field1.isReadOnly() != field2.isReadOnly()) {
//                    if (field1.isReadOnly()) {
//                        differences.add("Field " + field1.getFieldName() + " in entity " + entity1.getEntityName() + " is no longer read-only");
//                    } else {
//                        differences.add("Field " + field1.getFieldName() + " in entity " + entity1.getEntityName() + " is now read-only");
//                    }
//                }
                // Check for changes in readonly flag
//                if (field1.isReadOnly() != field2.isReadOnly()) {
//                    String message = field1.isReadOnly() ? "Removed" : "Added";
//                    differences.add(String.format("%s 'readonly' flag for field '%s' in entity '%s'.",
//                            message, field2.getFieldName(), entity2.getEntityName()));
//                }
            }

            // Check for added fields
            for (EntityField field2 : entity2.getFields()) {
                EntityField field1 = entity1.getFieldByName(field2.getFieldName());

                if (field1 == null) {
                    differences.add("Field " + field2.getFieldName() + " of type " + field2.getFieldType() + " has been added to entity " + entity2.getEntityName());
                    addedFields.put(field2.getFieldName(), Arrays.toString(new String[]{entity2.getEntityName(), field2.getFieldType()}));
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
                    differences.add("Field " + field.getFieldName() + " of type " + field.getFieldType() + " has been added to entity " + entity2.getEntityName());
                    addedFields.put(field.getFieldName(), Arrays.toString(new String[]{entity2.getEntityName(), field.getFieldType()}));
                }
            }
        }

        System.out.println("Added entities: " + addedEntities + "\n");
        System.out.println("Removed entities: " + removedEntities + "\n");
        System.out.println("Added fields: " + addedFields + "\n");
        System.out.println("Removed fields: " + removedFields + "\n");
        System.out.println("Changed field types: " + changedFieldTypes + "\n");

        return differences;
    }

}
