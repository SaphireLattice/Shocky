package pl.shockah.shocky.sql;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class ConnStatResultSet {
	public Connection c;
	public Statement p;
	public ResultSet rs;
	
	public ConnStatResultSet(Connection cn, Statement pn) {
		this.c = cn;
		this.p = pn;
		this.rs = null;
	}
	
	public ConnStatResultSet(Connection cn, Statement pn, ResultSet rsn) {
		this.c = cn;
		this.p = pn;
		this.rs = rsn;
	}
}
