package pl.shockah.shocky.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;


public class QueryDelete extends Query {
	private final String table;
	private final ArrayList<Criterion> criterions = new ArrayList<Criterion>();
	private int limitOffset = 0, limitCount = 1;
	
	public QueryDelete(String table) {
		this.table = table;
	}
	
	public void addCriterions(Criterion... criterions) {
		this.criterions.addAll(Arrays.asList(criterions));
	}
	
	public void setLimitOffset(int offset) {limitOffset = offset;}
	public void setLimitCount(int count) {limitCount = count;}
	public void setLimit(int offset, int count) {
		setLimitOffset(offset);
		setLimitCount(count);
	}
	
	@Deprecated
	public String getSQLQuery() {
		String clauseWhere = getWhereClause(criterions);
		String clauseLimit = limitOffset == 0 && limitCount == 1 ? "" : "LIMIT "+(limitOffset != 0 ? ""+limitOffset+"," : "")+limitCount;
		return "DELETE FROM "+table+(clauseWhere.isEmpty() ? "" : " "+clauseWhere)+(clauseLimit.isEmpty() ? "" : " "+clauseLimit);
	}
	
	public PreparedStatement getSQLQuery(Connection con) {
		StringBuilder sb = new StringBuilder("DELETE FROM ");
		String clauseWhere = getWhereClause(criterions);
		String clauseLimit = limitOffset == 0 && limitCount == 1 ? "" : "LIMIT "+(limitOffset != 0 ? ""+limitOffset+"," : "")+limitCount;
		sb.append(table)
		  .append(clauseWhere.isEmpty() ? "" : " "+clauseWhere)
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