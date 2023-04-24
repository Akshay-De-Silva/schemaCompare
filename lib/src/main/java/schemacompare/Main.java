package schemacompare;

import static schemacompare.Migrate.migrate;

public class Main {
    public static void main(String[] args) {
        if(args.length == 1) {
            migrate(args[0]);
        } else {
            System.out.println("Please provide the name of the migration");
        }
    }
}
