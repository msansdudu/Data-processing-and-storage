package ru.nsu.chebotareva.task3;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 5) {
            System.out.println("Usage: java -jar task3.jar <threads_N> <db_url> <db_user> <db_password> <xml_file_path>");
            return;
        }

        int threadsN;
        try {
            threadsN = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid thread count: " + args[0]);
            return;
        }

        String jdbcUrl = args[1];
        String dbUser = args[2];
        String dbPassword = args[3];
        String xmlFilePath = args[4];

        File xmlFile = new File(xmlFilePath);
        if (!xmlFile.exists() || !xmlFile.isFile()) {
            System.err.println("XML file not found: " + xmlFilePath);
            return;
        }

        DatabaseManager dbManager = new DatabaseManager(jdbcUrl, dbUser, dbPassword, threadsN);

        try {
            System.out.println("Starting streaming phase...");
            dbManager.createRawSchema();

            long fileSize = xmlFile.length();
            long chunkSize = fileSize / threadsN;

            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < threadsN; i++) {
                long startPos = i * chunkSize;
                long endPos = (i == threadsN - 1) ? fileSize : (i + 1) * chunkSize;
                boolean appendSuffix = (i != threadsN - 1);

                StreamWorker worker = new StreamWorker(i, xmlFilePath, startPos, endPos, dbManager, appendSuffix);
                Thread t = new Thread(worker);
                threads.add(t);
                t.start();
            }

            for (Thread t : threads) {
                t.join();
            }
            
            System.out.println("Streaming phase completed. Starting normalization phase...");
            runNormalizationPhase(dbManager);
            System.out.println("Normalization phase completed.");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            dbManager.close();
        }
    }

    private static void runNormalizationPhase(DatabaseManager dbManager) throws SQLException {
        try (Connection conn = dbManager.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP VIEW IF EXISTS sibling_view;");
            stmt.execute("DROP TABLE IF EXISTS person CASCADE;");
            
            String createTargetSchema = 
                "CREATE TABLE person (" +
                "    id          VARCHAR(50)     PRIMARY KEY," +
                "    first_name  VARCHAR(250)    NULL," +
                "    last_name   VARCHAR(250)    NULL," +
                "    gender      CHAR(1)         NULL CHECK (gender IN ('M', 'F'))," +
                "    spouse_id   VARCHAR(50)     NULL REFERENCES person(id)," +
                "    father_id   VARCHAR(50)     NULL REFERENCES person(id)," +
                "    mother_id   VARCHAR(50)     NULL REFERENCES person(id)," +
                "    UNIQUE(spouse_id)," +
                "    CHECK (id != spouse_id)," +
                "    CHECK (id != father_id)," +
                "    CHECK (id != mother_id)" +
                ");";
            stmt.execute(createTargetSchema);
            
            String funcsAndTriggers = 
                "CREATE OR REPLACE FUNCTION check_father_gender() " +
                "RETURNS TRIGGER AS $$ " +
                "BEGIN " +
                "    IF NEW.father_id IS NOT NULL THEN " +
                "        IF (SELECT gender FROM person WHERE id = NEW.father_id) != 'M' THEN " +
                "            RAISE EXCEPTION 'Отец должен быть мужского пола'; " +
                "        END IF; " +
                "    END IF; " +
                "    RETURN NEW; " +
                "END; " +
                "$$ LANGUAGE plpgsql;" +
                
                "CREATE TRIGGER trigger_check_father_gender " +
                "BEFORE INSERT OR UPDATE ON person " +
                "FOR EACH ROW EXECUTE FUNCTION check_father_gender();" +

                "CREATE OR REPLACE FUNCTION check_mother_gender() " +
                "RETURNS TRIGGER AS $$ " +
                "BEGIN " +
                "    IF NEW.mother_id IS NOT NULL THEN " +
                "        IF (SELECT gender FROM person WHERE id = NEW.mother_id) != 'F' THEN " +
                "            RAISE EXCEPTION 'Мать должна быть женского пола'; " +
                "        END IF; " +
                "    END IF; " +
                "    RETURN NEW; " +
                "END; " +
                "$$ LANGUAGE plpgsql;" +

                "CREATE TRIGGER trigger_check_mother_gender " +
                "BEFORE INSERT OR UPDATE ON person " +
                "FOR EACH ROW EXECUTE FUNCTION check_mother_gender();" +

                "CREATE OR REPLACE FUNCTION check_spouse_gender() " +
                "RETURNS TRIGGER AS $$ " +
                "BEGIN " +
                "    IF NEW.spouse_id IS NOT NULL AND NEW.gender IS NOT NULL THEN " +
                "        IF (SELECT gender FROM person WHERE id = NEW.spouse_id) = NEW.gender THEN " +
                "            RAISE EXCEPTION 'Супруги должны быть разного пола'; " +
                "        END IF; " +
                "    END IF; " +
                "    RETURN NEW; " +
                "END; " +
                "$$ LANGUAGE plpgsql;" +

                "CREATE TRIGGER trigger_check_spouse_gender " +
                "BEFORE INSERT OR UPDATE ON person " +
                "FOR EACH ROW EXECUTE FUNCTION check_spouse_gender();" +

                "CREATE VIEW sibling_view AS " +
                "SELECT " +
                "    p1.id AS person_id," +
                "    p2.id AS sibling_id " +
                "FROM person p1 " +
                "JOIN person p2 ON (" +
                "    (p1.father_id = p2.father_id AND p1.father_id IS NOT NULL) " +
                "    OR (p1.mother_id = p2.mother_id AND p1.mother_id IS NOT NULL)" +
                ") " +
                "WHERE p1.id != p2.id;";
            stmt.execute(funcsAndTriggers);
            
            // Normalization Phase Script
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_raw_person_id ON raw_person(id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_raw_rel_person_id ON raw_relationship(person_id);");
            
            // Clean people into a temp table
            stmt.execute(
                "CREATE TEMP TABLE cleaned_person AS " +
                "SELECT id, " +
                "       MAX(first_name) as first_name, " +
                "       MAX(last_name) as last_name, " +
                "       MAX(CASE " +
                "           WHEN UPPER(SUBSTRING(TRIM(gender) FROM 1 FOR 1)) = 'M' THEN 'M' " +
                "           WHEN UPPER(SUBSTRING(TRIM(gender) FROM 1 FOR 1)) = 'F' THEN 'F' " +
                "           ELSE NULL " +
                "       END) as gender " +
                "FROM raw_person " +
                "GROUP BY id;"
            );
            
            stmt.execute(
                "INSERT INTO person (id, first_name, last_name, gender) " +
                "SELECT id, first_name, last_name, gender " +
                "FROM cleaned_person;"
            );
            
            stmt.execute(
                "INSERT INTO person (id, gender) " +
                "SELECT DISTINCT rel_target, 'M' FROM raw_relationship " +
                "WHERE rel_type IN ('husband', 'father', 'son') AND rel_target != 'UNKNOWN' " +
                "AND rel_target NOT IN (SELECT id FROM person) " +
                "ON CONFLICT DO NOTHING;"
            );
            stmt.execute(
                "INSERT INTO person (id, gender) " +
                "SELECT DISTINCT rel_target, 'F' FROM raw_relationship " +
                "WHERE rel_type IN ('wife', 'mother', 'daughter') AND rel_target != 'UNKNOWN' " +
                "AND rel_target NOT IN (SELECT id FROM person) " +
                "ON CONFLICT DO NOTHING;"
            );
            // General missing persons
            stmt.execute(
                "INSERT INTO person (id) " +
                "SELECT DISTINCT rel_target FROM raw_relationship " +
                "WHERE rel_target != 'UNKNOWN' AND rel_target NOT IN (SELECT id FROM person) " +
                "ON CONFLICT DO NOTHING;"
            );
            
            // Update father
            stmt.execute(
                "UPDATE person p " +
                "SET father_id = r.rel_target " +
                "FROM raw_relationship r " +
                "WHERE p.id = r.person_id AND r.rel_type = 'father' AND r.rel_target != 'UNKNOWN' " +
                "AND r.rel_target IN (SELECT id FROM person WHERE gender = 'M');" // avoid trigger exception
            );
            
            // Update mother
            stmt.execute(
                "UPDATE person p " +
                "SET mother_id = r.rel_target " +
                "FROM raw_relationship r " +
                "WHERE p.id = r.person_id AND r.rel_type = 'mother' AND r.rel_target != 'UNKNOWN' " +
                "AND r.rel_target IN (SELECT id FROM person WHERE gender = 'F');"
            );
            
            // Update spouse (husband/wife/spouse)
            // Need to avoid the check_spouse_gender fail, so we make sure genders are different.
            stmt.execute(
                "UPDATE person p " +
                "SET spouse_id = sub.rel_target " +
                "FROM (" +
                "    SELECT person_id, rel_target " +
                "    FROM (" +
                "        SELECT r.person_id, r.rel_target, " +
                "               ROW_NUMBER() OVER(PARTITION BY r.rel_target ORDER BY r.person_id) as rn_target, " +
                "               ROW_NUMBER() OVER(PARTITION BY r.person_id ORDER BY r.rel_target) as rn_person " +
                "        FROM raw_relationship r " +
                "        JOIN person p2 ON r.rel_target = p2.id " +
                "        JOIN person p1 ON r.person_id = p1.id " +
                "        WHERE r.rel_type IN ('wife', 'husband', 'spouce', 'spouse') " +
                "          AND r.rel_target != 'UNKNOWN' " +
                "          AND p1.id != p2.id " +
                "          AND p1.gender != p2.gender " +
                "    ) ranked " +
                "    WHERE rn_target = 1 AND rn_person = 1 " +
                ") sub " +
                "WHERE p.id = sub.person_id;"
            );
        }
    }
}
