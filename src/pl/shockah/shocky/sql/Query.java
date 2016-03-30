package pl.shockah.shocky.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

public abstract class Query {
	protected static String getColumnsClause(Collection<String> list) {
		if (list == null || list.isEmpty()) return "*";
		StringBuilder sb = new StringBuilder();
		
		for (String s : list) {
			if (s == null) break;
			if (sb.length() != 0) sb.append(',');
			sb.append(s);
		}
		
		return sb.toString();
	}
	@Deprecated
	protected static String getValuesPairClause(Map<String,Object> list) {
		return getValuesPairClause(list, false);
	}
	protected static String getValuesPairClause(Map<String,Object> list, boolean prepare) {
		StringBuilder sb = new StringBuilder();
		
		for (Entry<String, Object> pair : list.entrySet()) {
			if (sb.length() != 0) sb.append(',');
			sb.append(pair.getKey());
			sb.append('=');
			Object value = pair.getValue();
			if (value instanceof Wildcard || prepare)
				sb.append('?');
			else if (value instanceof String)
				sb.append('\'').append(value.toString().replace("\\","\\\\").replace("'","\\'")).append('\'');
			else
				sb.append(value);
		}
		
		return sb.toString();
	}
	protected static String getValuesObjectClause(Collection<Object> list) {
		StringBuilder sb = new StringBuilder();
		
		for (Object o : list) {
			if (o == null) break;
			if (sb.length() != 0) sb.append(',');
			if (o instanceof Wildcard)
				sb.append('?');
			else if (o instanceof String)
				sb.append('\'').append(o.toString().replace("\\","\\\\").replace("'","\\'")).append('\'');
			else
				sb.append(o);
		}
		
		return sb.toString();
	}
	protected static String getOrderByClause(Map<String,Boolean> list) {
		if (list == null || list.isEmpty()) return "";
		
		StringBuilder sb = new StringBuilder("ORDER BY ");
		int i = 0;
		for (Entry<String, Boolean> pair : list.entrySet()) {
			if (i > 0) sb.append(',');
			sb.append(pair.getKey());
			sb.append(' ');
			sb.append(pair.getValue() ? "ASC" : "DESC");
			i++;
		}
		
		return sb.toString();
	}
	
	public static String getWhereClause(Collection<Criterion> list) {
		if (list == null || list.isEmpty()) return "";
		StringBuilder sb = new StringBuilder("WHERE ");
		
		int i = 0;
		boolean wasOR = false;
		for (Criterion c : list) {
			if (i > 0) {
				if (wasOR) {
					sb.append(" OR ");
				}
				else
					sb.append(" AND ");
			}
            if (c.useOR) {
                sb.append(" ( ");
            }
			//sb.append(c);
			sb.append(c.column).append(c.o).append("?");
			if (wasOR) {
				sb.append(" ) ");
				wasOR = false;
			}
			if (c.useOR) {
				wasOR = true;
			}
			i++;
		}
		
		return sb.toString();
	}
	
	public abstract String getSQLQuery();
	public abstract PreparedStatement getSQLQuery(Connection con);
}