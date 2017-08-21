package pl.shockah.shocky.sql;

public class Criterion {


	private String raw;
	
	public String column;
	public Operation o;
	public String value;
	public boolean useOR = false;
	
	public Criterion(String raw) {
		this.raw = raw;
	}
	
	public Criterion(String column, Operation o, String value) {
		this.column = column;
		this.o = o;
		this.value = value;
	}
	
	public Criterion setOR() {
		useOR = true;
		return this;
	}


	public String toString() {
		return '('+raw+')';
	}
	
	public static enum Operation {
		Equals("="),
		NotEquals("<>"),
		Lesser("<"),
		Greater(">"),
		LesserOrEqual("<="),
		GreaterOrEqual(">="),
		LIKE(" LIKE "),
		REGEXP(" REGEXP "),
		RAW("");
		
		private final String operation;
		
		Operation(String o) {
			operation = o;
		}
		
		public String toString() {
			return operation;
		}
	}
}