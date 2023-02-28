package schemacompare;

import java.nio.file.Path;
import java.nio.file.Paths;
import schemacompare.models.Module;

public class Main {
    public static void main(String[] args) {
        Path path1 = Paths.get("/home/wso2/IdeaProjects/schemaCompare/lib/src/test/resources/medicalEntities.bal");
        Path path2 = Paths.get("/home/wso2/IdeaProjects/schemaCompare/lib/src/test/resources/medicalEntitiesNew.bal");

        try {
            Module module1 = schemaCompare.getEntities(path1);
            System.out.println(module1.getModuleName());
            System.out.println(module1.getClientName());
            System.out.println(module1.getEntityMap());

            System.out.println("\n");

            Module module2 = schemaCompare.getEntities(path2);
            System.out.println(module2.getModuleName());
            System.out.println(module2.getClientName());
            System.out.println(module2.getEntityMap());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
