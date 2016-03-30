package pl.shockah.shocky.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


public class QuerySelect extends Query {
	private final String table;
	private final ArrayList<String> columns = new ArrayList<String>();
	private final ArrayList<Criterion> criterions = new ArrayList<Criterion>();
	private final Map<String,Boolean> orderby = new HashMap<String,Boolean>();
	private int limitOffset = 0, limitCount = -1;
	
	public QuerySelect(String table) {
		this.table = table;
	}
	
	public void addColumns(String... columns) {
		this.columns.addAll(Arrays.asList(columns));
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
		String clauseColumns = getColumnsClause(columns);
		String clauseWhere = getWhereClause(criterions);
		String clauseOrderBy = getOrderByClause(orderby);
		String clauseLimit = limitOffset == 0 && limitCount == -1 ? "" : "LIMIT "+(limitOffset != 0 ? ""+limitOffset+"," : "")+limitCount;
		return "SELECT "+clauseColumns+" FROM "+table+(clauseWhere.isEmpty() ? "" : " "+clauseWhere)+(clauseOrderBy.isEmpty() ? "" : " "+clauseOrderBy)+(clauseLimit.isEmpty() ? "" : " "+clauseLimit);
	}
	
	public PreparedStatement getSQLQuery(Connection con) {
		String clauseWhere = getWhereClause(criterions);
		String clauseColumns = getColumnsClause(columns);
		String clauseOrderBy = getOrderByClause(orderby);
		String clauseLimit = limitOffset == 0 && limitCount == -1 ? "" : "LIMIT "+(limitOffset != 0 ? ""+limitOffset+"," : "")+limitCount;
		StringBuilder sb = new StringBuilder("SELECT ");
		
		sb.append(clauseColumns)
		  .append(" FROM ")
		  .append(table)
		  .append(clauseWhere.isEmpty() ? "" : " "+clauseWhere)
		  .append(clauseOrderBy.isEmpty() ? "" : " "+clauseOrderBy)
		  .append(clauseLimit.isEmpty() ? "" : " "+clauseLimit);
		
		PreparedStatement p = null;
		int i=0;
		try {
			p = con.prepareStatement(sb.toString());
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