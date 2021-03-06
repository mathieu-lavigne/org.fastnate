package org.fastnate.generator.statements;

/**
 * Base class for statements generated by a {@link StatementsWriter}.
 *
 * @author Tobias Liefke
 */
public interface EntityStatement {

	/**
	 * Prints this statement as SQL.
	 *
	 * @return the SQL statement
	 */
	String toSql();

}
