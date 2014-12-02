package org.fastnate.generator.context;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.persistence.AssociationOverride;
import javax.persistence.CollectionTable;
import javax.persistence.ElementCollection;
import javax.persistence.Embeddable;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.MapKeyClass;
import javax.persistence.MapKeyColumn;
import javax.persistence.OneToMany;

import org.fastnate.generator.converter.EntityConverter;
import org.fastnate.generator.converter.ValueConverter;
import org.fastnate.generator.statements.EntityStatement;
import org.fastnate.generator.statements.InsertStatement;

import lombok.Getter;

import com.google.common.base.Preconditions;

/**
 * Describes a property of an {@link EntityClass} that is a {@link Map}.
 * 
 * @author Tobias Liefke
 * @param <E>
 *            The type of the container entity
 * @param <K>
 *            The type of the key of the map
 * @param <T>
 *            The type of the entity inside of the collection
 */
@Getter
public class MapProperty<E, K, T> extends PluralProperty<E, Map<K, T>, T> {

	private static String buildKeyColumn(final Field field, final String defaultKeyColumn) {
		final MapKeyColumn columnMetadata = field.getAnnotation(MapKeyColumn.class);
		if (columnMetadata != null && columnMetadata.name().length() > 0) {
			return columnMetadata.name();
		}
		return defaultKeyColumn;
	}

	/**
	 * Indicates that the given field references a map and may be used by an {@link MapProperty}.
	 * 
	 * @param field
	 *            the field to check
	 * @return {@code true} if an {@link MapProperty} may be created for the given field
	 */
	static boolean isMapField(final Field field) {
		return (field.getAnnotation(OneToMany.class) != null || field.getAnnotation(ManyToMany.class) != null || field
				.getAnnotation(ElementCollection.class) != null) && Map.class.isAssignableFrom(field.getType());
	}

	/** Indicates that this property is defined by another property on the target type. */
	private final String mappedBy;

	/** The class of the key of the map. */
	private final Class<K> keyClass;

	/** The converter for the target value, {@code null} if not a primitive value. */
	private final ValueConverter<K> keyConverter;

	/** The class of the value of the map. */
	private final Class<T> valueClass;

	/** The description of the value class, {@code null} if not an entity. */
	private final EntityClass<T> valueEntityClass;

	/** The converter for the target value, {@code null} if not a primitive value. */
	private final ValueConverter<T> valueConverter;

	/** The name of the modified table. */
	private final String table;

	/** The name of the column that contains the id of the entity. */
	private final String idColumn;

	/** The name of the column that contains the key. */
	private final String keyColumn;

	/** The name of the column that contains the value (or the id of the value). */
	private final String valueColumn;

	/**
	 * Creates a new map property.
	 * 
	 * @param sourceClass
	 *            the description of the current inspected class of the field
	 * @param field
	 *            the represented field
	 * @param override
	 *            the configured assocation override
	 */
	@SuppressWarnings("unchecked")
	public MapProperty(final EntityClass<?> sourceClass, final Field field, final AssociationOverride override) {
		super(sourceClass.getContext(), field);

		// Initialize the key information
		final MapKeyClass keyClassAnnotation = field.getAnnotation(MapKeyClass.class);
		this.keyClass = getFieldArgument(field, keyClassAnnotation != null ? keyClassAnnotation.value() : void.class, 0);
		this.keyColumn = buildKeyColumn(field, field.getName() + "_KEY");
		this.keyConverter = PrimitiveProperty.createConverter(field, this.keyClass, true);

		// Initialize the value description

		// Check if we are OneToMany or ManyToMany or ElementCollection and initialize accordingly
		final ElementCollection values = field.getAnnotation(ElementCollection.class);
		if (values != null) {
			// We are the owning side of the mapping
			this.mappedBy = null;

			// Initialize the table and id column name
			final CollectionTable collectionTable = field.getAnnotation(CollectionTable.class);
			this.table = buildTableName(collectionTable, sourceClass.getEntityName() + '_' + field.getName());
			this.idColumn = buildIdColumn(field, override, collectionTable, sourceClass.getEntityName() + '_'
					+ sourceClass.getIdColumn(field));

			// Initialize the target class description and columns
			this.valueClass = getFieldArgument(field, values.targetClass(), 1);
			if (this.valueClass.isAnnotationPresent(Embeddable.class)) {
				buildEmbeddedProperties(this.valueClass);
				this.valueEntityClass = null;
				this.valueConverter = null;
				this.valueColumn = null;
			} else {
				this.valueEntityClass = sourceClass.getContext().getDescription(this.valueClass);
				// Check for primitive value
				this.valueConverter = this.valueEntityClass == null ? PrimitiveProperty.createConverter(field,
						this.valueClass, false) : null;
				this.valueColumn = buildValueColumn(field, field.getName());
			}
		} else {
			// Entity mapping, either OneToMany or ManyToMany

			final OneToMany oneToMany = field.getAnnotation(OneToMany.class);
			if (oneToMany == null) {
				final ManyToMany manyToMany = field.getAnnotation(ManyToMany.class);
				Preconditions.checkArgument(manyToMany != null, field
						+ " is neither declared as OneToMany nor ManyToMany nor ElementCollection");
				this.valueClass = getFieldArgument(field, manyToMany.targetEntity(), 1);
				this.mappedBy = manyToMany.mappedBy().length() == 0 ? null : manyToMany.mappedBy();
			} else {
				this.valueClass = getFieldArgument(field, oneToMany.targetEntity(), 1);
				this.mappedBy = oneToMany.mappedBy().length() == 0 ? null : oneToMany.mappedBy();
			}

			// Resolve the target entity class
			this.valueEntityClass = sourceClass.getContext().getDescription(this.valueClass);

			// An entity mapping needs an entity class
			Preconditions.checkArgument(this.valueClass != null, "Map field " + field + " needs an entity as value");

			// No primitive value
			this.valueConverter = null;

			// Initialize the table and column names
			if (this.mappedBy != null) {
				// Bidirectional - use the column of the target class
				this.table = null;
				this.idColumn = null;
				this.valueColumn = null;
			} else {
				// Unidirectional and we need a mapping table
				final JoinTable joinTable = field.getAnnotation(JoinTable.class);
				this.table = buildTableName(field, override, joinTable, sourceClass.getTable() + '_'
						+ this.valueEntityClass.getTable());
				this.idColumn = buildIdColumn(field, override, joinTable,
						sourceClass.getTable() + '_' + sourceClass.getIdColumn(field));
				this.valueColumn = buildValueColumn(field,
						field.getName() + '_' + this.valueEntityClass.getIdColumn(field));
			}
		}
	}

	@Override
	public List<EntityStatement> buildAdditionalStatements(final E entity) {
		if (this.mappedBy != null) {
			return Collections.emptyList();
		}

		final List<EntityStatement> result = new ArrayList<>();
		final String sourceId = EntityConverter.getEntityReference(entity, getIdField(), getContext(), false);
		for (final Map.Entry<K, T> entry : getValue(entity).entrySet()) {
			String key;
			if (entry.getKey() == null) {
				key = "null";
			} else {
				key = this.keyConverter.getExpression(entry.getKey(), getContext());
			}

			if (isEmbedded()) {
				result.add(createEmbeddedPropertiesStatement(sourceId, key, entry.getValue()));
			} else {
				final EntityStatement statement = createDirectPropertyStatement(entity, sourceId, key, entry.getValue());
				if (statement != null) {
					result.add(statement);
				}
			}
		}

		return result;
	}

	private EntityStatement createDirectPropertyStatement(final E entity, final String key, final String sourceId,
			final T value) {
		String target;
		if (value == null) {
			target = "null";
		} else {
			if (this.valueConverter != null) {
				target = this.valueConverter.getExpression(value, getContext());
			} else {
				target = this.valueEntityClass.getEntityReference(value, getIdField(), false);
				if (target == null) {
					// Not created up to now
					this.valueEntityClass.markPendingUpdates(value, entity, this, key);
					return null;
				}
			}
		}

		final InsertStatement stmt = new InsertStatement(this.table, getContext().getDialect());
		stmt.addValue(this.idColumn, sourceId);
		stmt.addValue(this.keyColumn, key);
		stmt.addValue(this.valueColumn, target);
		return stmt;
	}

	private InsertStatement createEmbeddedPropertiesStatement(final String sourceId, final String key, final T value) {
		final InsertStatement stmt = new InsertStatement(this.table, getContext().getDialect());

		stmt.addValue(this.idColumn, sourceId);
		stmt.addValue(this.keyColumn, key);

		for (final SingularProperty<T, ?> property : getEmbeddedProperties()) {
			property.addInsertExpression(value, stmt);
		}
		return stmt;
	}

	@Override
	public Collection<?> findReferencedEntities(final E entity) {
		if (this.valueEntityClass != null) {
			return getValue(entity).values();
		} else if (isEmbedded()) {
			final List<Object> result = new ArrayList<>();
			for (final T value : getValue(entity).values()) {
				for (final Property<T, ?> property : getEmbeddedProperties()) {
					result.addAll(property.findReferencedEntities(value));
				}
			}
			return result;
		}

		return Collections.emptySet();
	}

	@Override
	public List<EntityStatement> generatePendingStatements(final E entity, final Object writtenEntity,
			final Object... arguments) {
		final String sourceId = EntityConverter.getEntityReference(entity, getIdField(), getContext(), false);
		final EntityStatement statement = createDirectPropertyStatement(entity, (String) arguments[0], sourceId,
				(T) writtenEntity);
		return statement == null ? Collections.<EntityStatement> emptyList() : Collections.singletonList(statement);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Map<K, T> getValue(final E entity) {
		final Map<K, T> value = super.getValue(entity);
		return value == null ? Collections.EMPTY_MAP : value;
	}

}