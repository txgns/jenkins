package metanectar.persistence;

import org.h2.api.Trigger;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A table schema.
 */
public class TableSchema<K> {

    private static final Map<Class, String> TYPES;

    static {
        Map<Class, String> types = new HashMap<Class, String>();
        types.put(String.class, "VARCHAR(255)");
        types.put(Integer.class, "INT");
        TYPES = Collections.unmodifiableMap(types);
    }

    private final Map<String, Column<?>> columnsByName;
    private final List<Column<?>> columnsByOrder;
    private final String name;
    private final List<Class<? extends Trigger>> triggers;

    public TableSchema(String name, PrimaryColumn<K> pk, Column<?>... columns) {
        this(name, Collections.<Class<? extends Trigger>>emptyList(), pk, columns);
    }

    private TableSchema(String name, List<Class<? extends Trigger>> triggers, PrimaryColumn<K> pk,
                        Column<?>... columns) {
        name.getClass();
        this.name = name;
        Map<String, Column<?>> columnsByName = new HashMap<String, Column<?>>(columns.length);
        List<Column<?>> columnsByOrder = new ArrayList<Column<?>>(columns.length);
        columnsByName.put(pk.getName(), pk);
        columnsByOrder.add(pk);
        for (Column<?> col : columns) {
            if (col instanceof PrimaryColumn) {
                throw new IllegalArgumentException("Only one primary key column supported");
            }
            if (columnsByName.containsKey(col.getName())) {
                throw new IllegalArgumentException("Duplicate column name: " + col.getName());
            }
            columnsByName.put(col.getName(), col);
            columnsByOrder.add(col);
        }
        this.columnsByName = columnsByName;
        this.columnsByOrder = columnsByOrder;
        this.triggers = new ArrayList<Class<? extends Trigger>>(triggers);
    }

    public static Column<String> _string(String name) {
        return col(name, String.class);
    }

    public static Column<Integer> _int(String name) {
        return col(name, Integer.class);
    }

    public static <T> Column<T> col(String name, Class<T> type) {
        return new Column<T>(name, type);
    }

    public TableSchema<K> withTrigger(Class<? extends Trigger>... trigger) {
        List<Class<? extends Trigger>> triggers = new ArrayList<Class<? extends Trigger>>(this.triggers);
        triggers.addAll(Arrays.asList(trigger));
        return new TableSchema<K>(name, triggers, primary(),
                columnsByOrder.subList(1, columnsByOrder.size()).toArray(new Column<?>[columnsByOrder.size() - 1]));
    }

    public String toDDL() {
        StringBuilder buf = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
        buf.append(name);
        buf.append(" ( ");
        boolean first = true;
        for (Column col : columnsByOrder) {
            if (first) {
                first = false;
            } else {
                buf.append(", ");
            }
            buf.append(col.getName());
            buf.append(' ');
            buf.append(TYPES.get(col.getType()));
            if (col.isPrimaryKey()) {
                buf.append(" PRIMARY KEY");
            } else if (col.isUnique()) {
                buf.append(" UNIQUE");
            }
        }
        buf.append(" )");
        int count=0;
        for (Class<? extends Trigger> trigger : triggers) {
            buf.append("; CREATE TRIGGER IF NOT EXISTS ");
            buf.append(name);
            buf.append("_T");
            buf.append(count++);
            buf.append(" AFTER INSERT, UPDATE, DELETE ON ");
            buf.append(name);
            buf.append(" FOR EACH ROW NOWAIT CALL \"");
            buf.append(trigger.getName());
            buf.append("\"");
        }
        return buf.toString();
    }

    public String getName() {
        return name;
    }

    public PrimaryColumn<K> primary() {
        return (PrimaryColumn<K>) columnsByOrder.get(0);
    }

    public Column<?> byName(String name) {
        return columnsByName.get(name);
    }

    public Column<?> bySqlIndex(int sqlIndex) {
        return byIndex(sqlIndex - 1);
    }

    public Column<?> byIndex(int index) {
        return index < 0 || index >= columnsByOrder.size() ? null : columnsByOrder.get(index);
    }

    public int columnCount() {
        return columnsByOrder.size();
    }

    public static class Column<T> {
        private final String name;
        private final Class<T> type;
        private final boolean unique;

        private Column(String name, Class<T> type) {
            this(name, type, false);
        }

        private Column(String name, Class<T> type, boolean unique) {
            name.getClass();
            type.getClass();
            this.name = name;
            this.type = type;
            this.unique = unique;
        }

        public final PrimaryColumn<T> primaryKey() {
            return new PrimaryColumn<T>(name, type);
        }

        public final Column<T> unique() {
            return new Column<T>(name, type, true);
        }

        public final String getName() {
            return name;
        }

        public final Class getType() {
            return type;
        }

        public boolean isPrimaryKey() {
            return false;
        }

        public boolean isUnique() {
            return unique;
        }

        @Override
        public final boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Column)) {
                return false;
            }

            Column column = (Column) o;

            if (!name.equals(column.name)) {
                return false;
            }
            if (!type.equals(column.type)) {
                return false;
            }

            return true;
        }

        @Override
        public final int hashCode() {
            return name.hashCode();
        }

        public T read(ResultSet resultSet) throws SQLException {
            if (String.class.equals(type)) {
                return (T) resultSet.getString(name);
            }
            if (Integer.class.equals(type)) {
                return (T) resultSet.getString(name);
            }
            return null;
        }

        public void setParameter(PreparedStatement s, int index, T value) throws SQLException {
            if (String.class.equals(type)) {
                s.setString(index, (String) value);
            } else if (Integer.class.equals(type)) {
                s.setInt(index, (Integer) value);
            }
        }
    }

    public static class PrimaryColumn<T> extends Column<T> {

        private PrimaryColumn(String name, Class<T> type) {
            super(name, type, true);
        }

        @Override
        public boolean isPrimaryKey() {
            return true;
        }
    }


}
