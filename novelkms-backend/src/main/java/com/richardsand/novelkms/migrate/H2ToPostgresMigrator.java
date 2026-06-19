package com.richardsand.novelkms.migrate;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.help.HelpFormatter;
import org.postgresql.util.PGobject;

/**
 * Schema-driven, one-shot migration utility for NovelKMS.
 *
 * Source: H2 file database (read-only)
 * Target: PostgreSQL schema already created by Flyway
 *
 * The migration:
 * - excludes Flyway's schema-history table
 * - discovers matching tables/columns case-insensitively
 * - orders inserts from PostgreSQL foreign keys
 * - truncates target application tables with CASCADE
 * - copies all data in one PostgreSQL transaction
 * - converts BLOB/CLOB/UUID/JSON values
 * - verifies source and target row counts before commit
 */
public final class H2ToPostgresMigrator {
    private static final String      DEFAULT_SOURCE_SCHEMA = "PUBLIC";
    private static final String      DEFAULT_TARGET_SCHEMA = "public";
    private static final Set<String> EXCLUDED_TABLES       = Set.of("flyway_schema_history");

    private H2ToPostgresMigrator() {
    }

    public static void main(String[] args) {
        Options           options = buildOptions();
        CommandLineParser parser  = new DefaultParser();

        try {
            CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption("help")) {
                printHelp(options);
                return;
            }

            MigrationOptions migrationOptions = MigrationOptions.from(cmd);
            run(migrationOptions);
        } catch (ParseException e) {
            System.err.println("Argument error: " + e.getMessage());
            try {
                printHelp(options);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            System.exit(2);
        } catch (Exception e) {
            System.err.println();
            System.err.println("MIGRATION FAILED: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static Options buildOptions() {
        Options options = new Options();

        options.addOption(Option.builder()
                .longOpt("h2-url")
                .hasArg()
                .argName("URL")
                .desc("H2 file JDBC URL; may also use NOVELKMS_H2_URL")
                .get());

        options.addOption(Option.builder()
                .longOpt("h2-user")
                .hasArg()
                .argName("USER")
                .desc("H2 username; default: sa")
                .get());

        options.addOption(Option.builder()
                .longOpt("h2-password")
                .hasArg()
                .argName("PASS")
                .desc("H2 password; default: empty")
                .get());

        options.addOption(Option.builder()
                .longOpt("pg-url")
                .hasArg()
                .argName("URL")
                .desc("PostgreSQL JDBC URL; may also use NOVELKMS_PG_URL")
                .get());

        options.addOption(Option.builder()
                .longOpt("pg-user")
                .hasArg()
                .argName("USER")
                .desc("PostgreSQL username; may also use NOVELKMS_PG_USER")
                .get());

        options.addOption(Option.builder()
                .longOpt("pg-password")
                .hasArg()
                .argName("PASS")
                .desc("PostgreSQL password; prefer NOVELKMS_PG_PASSWORD")
                .get());

        options.addOption(Option.builder()
                .longOpt("source-schema")
                .hasArg()
                .argName("SCHEMA")
                .desc("H2 source schema; default: PUBLIC")
                .get());

        options.addOption(Option.builder()
                .longOpt("target-schema")
                .hasArg()
                .argName("SCHEMA")
                .desc("PostgreSQL target schema; default: public")
                .get());

        options.addOption(Option.builder()
                .longOpt("batch-size")
                .hasArg()
                .argName("N")
                .desc("Insert batch size; default: 250")
                .get());

        options.addOption(Option.builder()
                .longOpt("dry-run")
                .desc("Copy and verify, then roll back")
                .get());

        options.addOption(Option.builder()
                .longOpt("allow-nonempty-target")
                .desc("Explicitly authorize truncating non-empty PostgreSQL application tables")
                .get());

        options.addOption(new Option("h", "help", false, "Show help"));
        return options;
    }

    private static void printHelp(Options options) throws IOException {
        HelpFormatter formatter = HelpFormatter.builder().get();
        formatter.printHelp(
                "java -jar novelkms-db-migrator-1.0.0.jar [options]",
                "NovelKMS H2 -> PostgreSQL migrator",
                options,
                "",
                true);
    }

    private static void run(MigrationOptions config) throws Exception {
        Class.forName("org.h2.Driver");
        Class.forName("org.postgresql.Driver");

        Properties h2Props = new Properties();
        if (config.h2User() != null)
            h2Props.setProperty("user", config.h2User());
        if (config.h2Password() != null)
            h2Props.setProperty("password", config.h2Password());

        Properties pgProps = new Properties();
        pgProps.setProperty("user", config.pgUser());
        pgProps.setProperty("password", config.pgPassword());

        System.out.println("Opening H2 source read-only...");
        try (Connection source = DriverManager.getConnection(ensureH2ReadOnly(config.h2Url()), h2Props);
                Connection target = DriverManager.getConnection(config.pgUrl(), pgProps)) {

            source.setReadOnly(true);
            target.setAutoCommit(false);

            try {
                migrate(source, target, config);
                if (config.dryRun()) {
                    target.rollback();
                    System.out.println("\nDry run complete; PostgreSQL transaction rolled back.");
                } else {
                    target.commit();
                    System.out.println("\nMigration committed successfully.");
                }
            } catch (Exception e) {
                target.rollback();
                throw e;
            }
        }
    }

    private static String ensureH2ReadOnly(String url) {
        if (url.toUpperCase(Locale.ROOT).contains("ACCESS_MODE_DATA="))
            return url;
        return url + (url.contains(";") ? ";" : ";") + "ACCESS_MODE_DATA=r";
    }

    private static void migrate(Connection source, Connection target, MigrationOptions config) throws Exception {
        Map<String, TableInfo> sourceTables = loadTables(source, config.sourceSchema());
        Map<String, TableInfo> targetTables = loadTables(target, config.targetSchema());

        List<TablePair> pairs = matchTables(sourceTables, targetTables);
        if (pairs.isEmpty()) {
            throw new IllegalStateException("No matching application tables were found.");
        }

        List<String> targetOrder = topologicalOrder(target, config.targetSchema(),
                pairs.stream().map(p -> p.target.name).collect(Collectors.toSet()));

        Map<String, TablePair> pairByTarget = pairs.stream().collect(Collectors.toMap(p -> p.target.name, p -> p));

        List<TablePair> orderedPairs = targetOrder.stream()
                .map(pairByTarget::get)
                .filter(Objects::nonNull)
                .toList();

        printPlan(orderedPairs);

        validateTargetColumns(orderedPairs);

        if (!config.allowNonEmptyTarget()) {
            assertTargetEmpty(target, config.targetSchema(), orderedPairs);
        }

        truncateTarget(target, config.targetSchema(), orderedPairs);

        Map<String, Long> copied = new LinkedHashMap<>();
        for (TablePair pair : orderedPairs) {
            long count = copyTable(source, target, config, pair);
            copied.put(pair.target.name, count);
        }

        verifyCounts(source, target, config, orderedPairs, copied);
    }

    private static Map<String, TableInfo> loadTables(Connection connection, String schema)
            throws SQLException {
        DatabaseMetaData       md     = connection.getMetaData();
        Map<String, TableInfo> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        try (ResultSet rs = md.getTables(null, schema, "%", new String[] { "TABLE" })) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (name == null || EXCLUDED_TABLES.contains(name.toLowerCase(Locale.ROOT)))
                    continue;
                TableInfo table = new TableInfo(name);
                result.put(name, table);
            }
        }

        for (TableInfo table : result.values()) {
            try (ResultSet rs = md.getColumns(null, schema, table.name, "%")) {
                while (rs.next()) {
                    ColumnInfo column = new ColumnInfo(
                            rs.getString("COLUMN_NAME"),
                            rs.getInt("DATA_TYPE"),
                            rs.getString("TYPE_NAME"),
                            "NO".equalsIgnoreCase(rs.getString("IS_NULLABLE")),
                            rs.getString("COLUMN_DEF"),
                            safeYes(rs, "IS_AUTOINCREMENT"),
                            safeYes(rs, "IS_GENERATEDCOLUMN"));
                    table.columns.put(column.name, column);
                }
            }
        }
        return result;
    }

    private static boolean safeYes(ResultSet rs, String column) {
        try {
            return "YES".equalsIgnoreCase(rs.getString(column));
        } catch (SQLException ignored) {
            return false;
        }
    }

    private static List<TablePair> matchTables(
            Map<String, TableInfo> source,
            Map<String, TableInfo> target) {
        List<TablePair> result = new ArrayList<>();
        for (TableInfo targetTable : target.values()) {
            TableInfo sourceTable = source.get(targetTable.name);
            if (sourceTable != null)
                result.add(new TablePair(sourceTable, targetTable));
        }
        return result;
    }

    private static List<String> topologicalOrder(
            Connection target, String schema, Set<String> includedTables) throws SQLException {
        Map<String, Set<String>> dependencies = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String table : includedTables)
            dependencies.put(table, new TreeSet<>(String.CASE_INSENSITIVE_ORDER));

        DatabaseMetaData md = target.getMetaData();
        for (String table : includedTables) {
            try (ResultSet rs = md.getImportedKeys(null, schema, table)) {
                while (rs.next()) {
                    String parent = rs.getString("PKTABLE_NAME");
                    String child  = rs.getString("FKTABLE_NAME");
                    if (parent != null && child != null
                            && includedTables.stream().anyMatch(t -> t.equalsIgnoreCase(parent))
                            && !parent.equalsIgnoreCase(child)) {
                        dependencies.get(child).add(resolveCase(includedTables, parent));
                    }
                }
            }
        }

        List<String> order     = new ArrayList<>();
        Set<String>  remaining = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        remaining.addAll(includedTables);

        while (!remaining.isEmpty()) {
            List<String> ready = remaining.stream()
                    .filter(t -> dependencies.getOrDefault(t, Set.of()).stream()
                            .noneMatch(remaining::contains))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();

            if (ready.isEmpty()) {
                // Cyclic FK graph: PostgreSQL TRUNCATE CASCADE is safe, but insertion may
                // require deferred constraints. Put remaining tables in deterministic order.
                System.out.println("WARNING: cyclic foreign-key graph detected among: " + remaining);
                order.addAll(remaining.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList());
                break;
            }

            order.addAll(ready);
            remaining.removeAll(ready);
        }
        return order;
    }

    private static String resolveCase(Set<String> names, String wanted) {
        return names.stream().filter(n -> n.equalsIgnoreCase(wanted)).findFirst().orElse(wanted);
    }

    private static void printPlan(List<TablePair> pairs) {
        System.out.println("\nMigration plan:");
        for (int i = 0; i < pairs.size(); i++) {
            TablePair        p       = pairs.get(i);
            List<ColumnPair> columns = matchingColumns(p);
            System.out.printf("  %2d. %-30s (%d columns)%n",
                    i + 1, p.target.name, columns.size());
        }
    }

    private static void validateTargetColumns(List<TablePair> pairs) {
        List<String> errors = new ArrayList<>();
        for (TablePair pair : pairs) {
            Set<String> sourceNames = pair.source.columns.keySet();
            for (ColumnInfo targetColumn : pair.target.columns.values()) {
                boolean present         = sourceNames.stream()
                        .anyMatch(n -> n.equalsIgnoreCase(targetColumn.name));
                boolean safelyGenerated = targetColumn.autoIncrement
                        || targetColumn.generated
                        || targetColumn.defaultValue != null;
                if (!present && targetColumn.notNull && !safelyGenerated) {
                    errors.add(pair.target.name + "." + targetColumn.name
                            + " is required in PostgreSQL but absent from H2");
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Schema mismatch:\n  - "
                    + String.join("\n  - ", errors));
        }
    }

    private static void assertTargetEmpty(
            Connection target, String schema, List<TablePair> pairs) throws SQLException {
        List<String> nonEmpty = new ArrayList<>();
        for (TablePair pair : pairs) {
            long count = count(target, schema, pair.target.name);
            if (count > 0)
                nonEmpty.add(pair.target.name + "=" + count);
        }
        if (!nonEmpty.isEmpty()) {
            throw new IllegalStateException(
                    "PostgreSQL application tables are not empty: " + String.join(", ", nonEmpty)
                            + ". Re-run with --allow-nonempty-target to explicitly authorize truncation.");
        }
    }

    private static void truncateTarget(
            Connection target, String schema, List<TablePair> pairs) throws SQLException {
        String tables = pairs.stream()
                .map(p -> quote(schema) + "." + quote(p.target.name))
                .collect(Collectors.joining(", "));
        String sql    = "TRUNCATE TABLE " + tables + " RESTART IDENTITY CASCADE";
        System.out.println("\nTruncating PostgreSQL application tables...");
        try (Statement st = target.createStatement()) {
            st.execute(sql);
        }
    }

    private static long copyTable(
            Connection source, Connection target, MigrationOptions config, TablePair pair) throws Exception {
        List<ColumnPair> columns = matchingColumns(pair);
        if (columns.isEmpty()) {
            System.out.println("Skipping " + pair.target.name + " (no matching columns)");
            return 0;
        }

        String selectSql = "SELECT "
                + columns.stream().map(c -> quote(c.source.name)).collect(Collectors.joining(", "))
                + " FROM " + quote(config.sourceSchema()) + "." + quote(pair.source.name);

        String insertSql = "INSERT INTO " + quote(config.targetSchema()) + "." + quote(pair.target.name)
                + " (" + columns.stream().map(c -> quote(c.target.name))
                        .collect(Collectors.joining(", "))
                + ") VALUES ("
                + String.join(", ", Collections.nCopies(columns.size(), "?")) + ")";

        long count = 0;
        System.out.printf("Copying %-30s", pair.target.name);

        try (Statement select = source.createStatement();
                ResultSet rs = select.executeQuery(selectSql);
                PreparedStatement insert = target.prepareStatement(insertSql)) {

            int batchCount = 0;
            while (rs.next()) {
                for (int i = 0; i < columns.size(); i++) {
                    ColumnPair column = columns.get(i);
                    Object     value  = readValue(rs, i + 1, column.source);
                    bindValue(insert, i + 1, value, column.target);
                }
                insert.addBatch();
                batchCount++;
                count++;

                if (batchCount >= config.batchSize()) {
                    insert.executeBatch();
                    batchCount = 0;
                }
            }
            if (batchCount > 0)
                insert.executeBatch();
        }

        System.out.printf(" %d rows%n", count);
        return count;
    }

    private static List<ColumnPair> matchingColumns(TablePair pair) {
        List<ColumnPair> result = new ArrayList<>();
        for (ColumnInfo targetColumn : pair.target.columns.values()) {
            ColumnInfo sourceColumn = pair.source.columns.get(targetColumn.name);
            if (sourceColumn != null && !targetColumn.generated) {
                result.add(new ColumnPair(sourceColumn, targetColumn));
            }
        }
        return result;
    }

    private static Object readValue(ResultSet rs, int index, ColumnInfo sourceColumn)
            throws Exception {
        Object value = rs.getObject(index);
        if (value == null)
            return null;

        if (value instanceof Blob blob) {
            try (InputStream in = blob.getBinaryStream()) {
                return in.readAllBytes();
            }
        }
        if (value instanceof Clob clob) {
            try (Reader reader = clob.getCharacterStream()) {
                StringBuilder sb     = new StringBuilder();
                char[]        buffer = new char[8192];
                int           n;
                while ((n = reader.read(buffer)) >= 0)
                    sb.append(buffer, 0, n);
                return sb.toString();
            }
        }
        if (value instanceof InputStream in) {
            try (in) {
                return in.readAllBytes();
            }
        }
        return value;
    }

    private static void bindValue(
            PreparedStatement ps, int index, Object value, ColumnInfo targetColumn)
            throws SQLException {
        if (value == null) {
            ps.setNull(index, targetColumn.jdbcType);
            return;
        }

        String targetType = targetColumn.typeName == null
                ? ""
                : targetColumn.typeName.toLowerCase(Locale.ROOT);

        if ("json".equals(targetType) || "jsonb".equals(targetType)) {
            PGobject json = new PGobject();
            json.setType(targetType);
            json.setValue(value.toString());
            ps.setObject(index, json);
            return;
        }

        if ("uuid".equals(targetType)) {
            if (value instanceof UUID uuid)
                ps.setObject(index, uuid);
            else
                ps.setObject(index, UUID.fromString(value.toString()));
            return;
        }

        if (value instanceof byte[] bytes) {
            ps.setBytes(index, bytes);
        } else if (value instanceof UUID uuid) {
            ps.setObject(index, uuid);
        } else if (value instanceof LocalDateTime ldt) {
            ps.setTimestamp(index, Timestamp.valueOf(ldt));
        } else if (value instanceof LocalDate ld) {
            ps.setDate(index, Date.valueOf(ld));
        } else if (value instanceof LocalTime lt) {
            ps.setTime(index, Time.valueOf(lt));
        } else if (value instanceof OffsetDateTime odt) {
            ps.setObject(index, odt);
        } else if (value instanceof Instant instant) {
            ps.setTimestamp(index, Timestamp.from(instant));
        } else if (value instanceof Boolean b) {
            ps.setBoolean(index, b);
        } else if (value instanceof BigDecimal bd) {
            ps.setBigDecimal(index, bd);
        } else {
            ps.setObject(index, value);
        }
    }

    private static void verifyCounts(
            Connection source,
            Connection target,
            MigrationOptions config,
            List<TablePair> pairs,
            Map<String, Long> copied) throws SQLException {

        System.out.println("\nVerifying row counts:");
        List<String> mismatches = new ArrayList<>();

        for (TablePair pair : pairs) {
            long   sourceCount = count(source, config.sourceSchema(), pair.source.name);
            long   targetCount = count(target, config.targetSchema(), pair.target.name);
            String status      = sourceCount == targetCount ? "OK" : "MISMATCH";
            System.out.printf("  %-30s H2=%-8d PostgreSQL=%-8d %s%n",
                    pair.target.name, sourceCount, targetCount, status);
            if (sourceCount != targetCount) {
                mismatches.add(pair.target.name + ": H2=" + sourceCount
                        + ", PostgreSQL=" + targetCount);
            }
        }

        if (!mismatches.isEmpty()) {
            throw new IllegalStateException("Count verification failed: "
                    + String.join("; ", mismatches));
        }
    }

    private static long count(Connection connection, String schema, String table)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + quote(schema) + "." + quote(table);
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static final class TableInfo {
        final String                  name;
        final Map<String, ColumnInfo> columns = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        TableInfo(String name) {
            this.name = name;
        }
    }

    private record ColumnInfo(
            String name,
            int jdbcType,
            String typeName,
            boolean notNull,
            String defaultValue,
            boolean autoIncrement,
            boolean generated) {
    }

    private record TablePair(TableInfo source, TableInfo target) {
    }

    private record ColumnPair(ColumnInfo source, ColumnInfo target) {
    }

    private record MigrationOptions(
            String h2Url,
            String h2User,
            String h2Password,
            String pgUrl,
            String pgUser,
            String pgPassword,
            String sourceSchema,
            String targetSchema,
            int batchSize,
            boolean dryRun,
            boolean allowNonEmptyTarget) {

        static MigrationOptions from(CommandLine cmd) throws ParseException {
            String h2Url = required(
                    firstNonBlank(cmd.getOptionValue("h2-url"), System.getenv("NOVELKMS_H2_URL")),
                    "--h2-url or NOVELKMS_H2_URL");

            String pgUrl = required(
                    firstNonBlank(cmd.getOptionValue("pg-url"), System.getenv("NOVELKMS_PG_URL")),
                    "--pg-url or NOVELKMS_PG_URL");

            String pgUser = required(
                    firstNonBlank(cmd.getOptionValue("pg-user"), System.getenv("NOVELKMS_PG_USER")),
                    "--pg-user or NOVELKMS_PG_USER");

            String pgPassword = required(
                    firstNonBlank(
                            cmd.getOptionValue("pg-password"),
                            System.getenv("NOVELKMS_PG_PASSWORD")),
                    "--pg-password or NOVELKMS_PG_PASSWORD");

            String batchSizeText = firstNonBlank(cmd.getOptionValue("batch-size"), "250");
            int    batchSize;
            try {
                batchSize = Integer.parseInt(batchSizeText);
            } catch (NumberFormatException e) {
                throw new ParseException("--batch-size must be an integer: " + batchSizeText);
            }
            if (batchSize <= 0) {
                throw new ParseException("--batch-size must be greater than zero");
            }

            return new MigrationOptions(
                    h2Url,
                    firstNonBlank(
                            cmd.getOptionValue("h2-user"),
                            System.getenv("NOVELKMS_H2_USER"),
                            "sa"),
                    firstNonBlank(
                            cmd.getOptionValue("h2-password"),
                            System.getenv("NOVELKMS_H2_PASSWORD"),
                            ""),
                    pgUrl,
                    pgUser,
                    pgPassword,
                    firstNonBlank(cmd.getOptionValue("source-schema"), DEFAULT_SOURCE_SCHEMA),
                    firstNonBlank(cmd.getOptionValue("target-schema"), DEFAULT_TARGET_SCHEMA),
                    batchSize,
                    cmd.hasOption("dry-run"),
                    cmd.hasOption("allow-nonempty-target"));
        }

        private static String required(String value, String label) throws ParseException {
            if (value == null || value.isBlank()) {
                throw new ParseException(label + " is required");
            }
            return value;
        }

        private static String firstNonBlank(String... values) {
            for (String value : values) {
                if (value != null && !value.isBlank())
                    return value;
            }
            return null;
        }
    }

}
