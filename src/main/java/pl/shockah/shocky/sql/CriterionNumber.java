package pl.shockah.shocky.sql;

public class CriterionNumber extends Criterion {
	public CriterionNumber(String column, Operation o, long value) {
		super(column, o, String.valueOf(value));
	}
}