package pl.shockah.shocky.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import org.sqlite.SQLiteConfig;

import pl.shockah.shocky.Data;

public class SQL {
	private static Connection conn = null;
	public static Map<String,PreparedStatement> statements = new HashMap<String,PreparedStatement>();

	public static void raw(String query) {execute(query);}
	public static ResultSet select(QuerySelect query) {return executeQuery(query.getSQLQuery());}
	public static ConnStatResultSet select(QuerySelect query, Boolean close) {return executeQuery(query.getSQLQuery(),close);}
	public static void delete(QueryDelete query) {execute(query.getSQLQuery());}
	public static int update(QueryUpdate query) {return updateResultSet(query.getSQLQuery());}
	
	public static void init() {
		try {
            //Class.forName("com.mysql.jdbc.Driver").newInstance();
			Class.forName("org.sqlite.JDBC").newInstance();
            Class.forName("pl.shockah.shocky.sql.Criterion");
            Class.forName("pl.shockah.shocky.sql.CriterionNumber");
            Class.forName("pl.shockah.shocky.sql.CriterionString");
            Class.forName("pl.shockah.shocky.sql.Wildcard");
            Class.forName("pl.shockah.shocky.sql.Factoid");
            Class.forName("pl.shockah.shocky.sql.Query");
            Class.forName("pl.shockah.shocky.sql.QueryDelete");
            Class.forName("pl.shockah.shocky.sql.QueryInsert");
            Class.forName("pl.shockah.shocky.sql.QueryUpdate");
        } catch (Exception ex) {
        	ex.printStackTrace();
        }
	}
	
	public synchronized static Connection getSQLConnection() {
		try {
			/*if (conn == null || !conn.isValid(1)) {
				if (conn != null && !statements.isEmpty()) {
					Iterator<PreparedStatement> iter = statements.values().iterator();
					while(iter.hasNext()) {
						PreparedStatement p = iter.next();
						p.close();
						iter.remove();
					}
				}
				
				conn = DriverManager.getConnection("jdbc:sqlite:shocky.db", null);
				*/
			SQLiteConfig sqlconf = new SQLiteConfig();
			sqlconf.enableLoadExtension(true);
			conn = sqlconf.createConnection("jdbc:sqlite:shocky.db");
			Statement stmt = conn.createStatement();
			stmt.setQueryTimeout(30);
			try (ResultSet rs = stmt.executeQuery("SELECT load_extension('/usr/lib/sqlite3/pcre');")) {
				while (rs.next()) {}
			   	rs.close();
			}
		    stmt.close();
			//}
		} catch (Exception e) {
			e.printStackTrace();
			try {
			conn.close();
			} catch(SQLException ei)
			{}
			conn = null;
		}
		return conn;
	}
	
	public static ResultSet executeQuery(String query) {
		ConnStatResultSet tmpcsrs = executeQuery(query, true);
		try {
			tmpcsrs.c.close();
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
		ResultSet ret = tmpcsrs.rs;
		return ret;
	}
	public static ConnStatResultSet executeQuery(String query, Boolean close) {
		try {
			Connection c = SQL.getSQLConnection();
			Statement s = c.createStatement();
			ResultSet rs = s.executeQuery(query);
			if(close){
				s.close();
				c.close();
			}
			return new ConnStatResultSet(c,s,rs);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static void execute(String query) {
		Statement s = null;
		try {
			Connection tmpc= getSQLConnection();
			s = tmpc.createStatement();
			s.execute(query);
			s.close();
			tmpc.close();
		} catch (SQLException e) {
			e.printStackTrace();
			System.out.println(query);
		}
	}
	
	public static int updateResultSet(String query) {
		Statement s = null;
		try {
			Connection tmpc= getSQLConnection();
			s = tmpc.createStatement();
			int tmp = s.executeUpdate(query);
			s.close();
			tmpc.close();
			return tmp;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return 0;
	}
	
	public static int insert(QueryInsert query) {
		PreparedStatement s = null;
		try {
			Connection tmpc= getSQLConnection();
			s = query.getSQLQuery(tmpc);
			int tmp = s.executeUpdate();
			s.close();
			tmpc.close();
			return tmp;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return 0;
	}
	
	public static String getTable(String name) {
		return Data.config.getString("main-sqlprefix")+name;
	}
}