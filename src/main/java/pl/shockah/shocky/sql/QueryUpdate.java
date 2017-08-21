package pl.shockah.shocky.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class QueryUpdate extends Query {
	private final String table;
	private final ArrayList<Criterion> criterions = new ArrayList<Criterion>();
	private final Map<String,Object> values = new HashMap<String,Object>();
	private final Map<String,Boolean> orderby = new HashMap<String,Boolean>();
	private int limitOffset = 0, limitCount = 1;
	
	public QueryUpdate(String table) {
		this.table = table;
	}
	
	public void set(String column, Object value) {
		values.put(column,value);
	}

	public void addCriterions(Criterion... criterions) {
		this.criterions.addAll(Arrays.asList(criterions));
	}
	
	public void addOrder(String column) {addOrder(column,true);}
	public void addOrder(String column, boolean ascending) {
		orderby.put(column,ascending);
	}
	
	public void setLimitOffset(int offset) {limitOffset = offset;}
	public void setLimitCount(int count) {limitCount = count;}
	public void setLimit(int offset, int count) {
		setLimitOffset(offset);
		setLimitCount(count);
	}
	
	@Deprecated
	public String getSQLQuery() {
		StringBuilder sb = new StringBuilder("UPDATE ");
		String clauseValues = getValuesPairClause(values);
		String clauseWhere = getWhereClause(criterions);
		String clauseOrderBy = getOrderByClause(orderby);
		String clauseLimit = limitOffset == 0 && limitCount == 1 ? "" : "LIMIT "+(limitOffset != 0 ? ""+limitOffset+"," : "")+limitCount;
		sb.append(table)
		  .append(" SET ")
		  .append(clauseValues)
		  .append(clauseWhere.isEmpty() ? "" : " "+clauseWhere)
		  .append(clauseOrderBy.isEmpty() ? "" : " "+clauseOrderBy)
		  .append(clauseLimit.isEmpty() ? "" : " "+clauseLimit);
		return sb.toString();
	}
	
	public PreparedStatement getSQLQuery(Connection con) {
		String clauseValues = getValuesPairClause(values, true);
		String clauseWhere = getWhereClause(criterions);
		String clauseOrderBy = getOrderByClause(orderby);
		String clauseLimit = limitOffset == 0 && limitCount == 1 ? "" : "LIMIT "+(limitOffset != 0 ? ""+limitOffset+"," : "")+limitCount;

		StringBuilder sb = new StringBuilder("UPDATE ");
		sb.append(table)
		  .append(" SET ")
		  .append(clauseValues)
		  .append(clauseWhere.isEmpty() ? "" : " "+clauseWhere)
		  .append(clauseOrderBy.isEmpty() ? "" : " "+clauseOrderBy)
		  .append(clauseLimit.isEmpty() ? "" : " "+clauseLimit);
		
		PreparedStatement p = null;
		int i=0;
		try {
			p = con.prepareStatement(sb.toString());
			for (Entry<String, Object> pair : values.entrySet()) {
				i++;
				p.setObject(i, pair.getValue());
			}
			for (Criterion c : criterions) {
				i++;
				p.setObject(i, c.value);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return p;
	}
}