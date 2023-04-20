package schemacompare;

import static schemacompare.Migrate.migrate;

public class Main {
    public static void main(String[] args) {
        migrate("/home/wso2/IdeaProjects/schemaCompare/lib/src/myModels", "secondMigration");
    }
}
