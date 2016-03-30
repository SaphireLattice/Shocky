package pl.shockah.shocky.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;

public class QueryInsert extends Query {
	private final String table;
	private final Map<String,Object> values = new HashMap<String,Object>();
	
	public QueryInsert(String table) {
		this.table = table;
	}
	
	public void add(String column, Object value) {
		values.put(column, value);
	}
	
	@Deprecated
	public String getSQLQuery() {
		String clauseColumns = getColumnsClause(values.keySet());
		String clauseValues = getValuesObjectClause(values.values());
		StringBuilder sb = new StringBuilder("INSERT INTO ");
		sb.append(table)
		  .append(" (")
		  .append(clauseColumns)
		  .append(") VALUES(")
		  .append(clauseValues)
		  .append(")");
		return sb.toString();
	}
	
	public PreparedStatement getSQLQuery(Connection con) {
		Map<Integer,String> keys = new HashMap<Integer,String>();
		
		StringBuilder sb = new StringBuilder("INSERT INTO ");
		sb.append(table).append('(');
		
		int i = 0;
		for (String key : values.keySet()) {
			if (i > 0) sb.append(",");
			i++;
			sb.append(key);
			keys.put(i, key);
		}
		sb.append(") VALUES(");
		for (; i>=1;i--) {
			sb.append('?');
			if (i > 1) sb.append(',');
		}
		sb.append(')');
		
		PreparedStatement p = null;
		i=0;
		try {
			p = con.prepareStatement(sb.toString());
			for (; i < values.size(); i++) {
				p.setObject(i+1, values.get(keys.get(i+1)));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return p;
	}
}