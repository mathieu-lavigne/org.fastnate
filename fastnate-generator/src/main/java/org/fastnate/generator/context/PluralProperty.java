package org.fastnate.generator.context;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.persistence.Access;
import javax.persistence.AssociationOverride;
import javax.persistence.AttributeOverride;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Embeddable;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.MapsId;
import javax.persistence.OneToMany;

import org.fastnate.generator.converter.EntityConverter;
import org.fastnate.generator.converter.ValueConverter;
import org.fastnate.generator.dialect.GeneratorDialect;
import org.fastnate.generator.statements.ColumnExpression;
import org.fastnate.generator.statements.PrimitiveColumnExpression;
import org.fastnate.generator.statements.StatementsWriter;
import org.fastnate.generator.statements.TableStatement;
import org.fastnate.util.ClassUtil;

import com.google.common.base.Preconditions;

import lombok.Getter;

/**
 * Base class for {@link MapProperty} and {@link CollectionProperty}.
 *
 * @author Tobias Liefke
 * @param <E>
 *            The type of the container class
 * @param <C>
 *            The type of the collection of map
 * @param <T>
 *            The type of the elements in the collection
 */
@Getter
public abstract class PluralProperty<E, C, T> extends Property<E, C> {

	/**
	 * Builds the name of the column that contains the ID of the entity for the given attribute.
	 *
	 * @param attribute
	 *            the accessor for the inspected attribute
	 * @param override
	 *            contains optional override options
	 * @param collectionMetadata
	 *            the default join column
	 * @param defaultIdColumn
	 *            the default name for the column, if {@code joinColumn} is empty or {@code null}
	 * @return the column name
	 */
	protected static String buildIdColumn(final AttributeAccessor attribute, final AssociationOverride override,
			final CollectionTable collectionMetadata, final String defaultIdColumn) {
		return buildIdColumn(attribute, override, collectionMetadata != null ? collectionMetadata.joinColumns() : null,
				defaultIdColumn);
	}

	/**
	 * Builds the name of the column that contains the ID of the entity for the given attribute.
	 *
	 * @param attribute
	 *            the inspected attribute
	 * @param override
	 *            contains optional override options
	 * @param joinColumns
	 *            the default join columns
	 * @param defaultIdColumn
	 *            the default name for the column, if {@code joinColumn} is empty or {@code null}
	 * @return the column name
	 */
	private static String buildIdColumn(final AttributeAccessor attribute, final AssociationOverride override,
			final JoinColumn[] joinColumns, final String defaultIdColumn) {
		if (override != null && override.joinColumns().length > 0) {
			final JoinColumn joinColumn = override.joinColumns()[0];
			if (joinColumn.name().length() > 0) {
				return joinColumn.name();
			}
		}

		final JoinColumn joinColumn = attribute.getAnnotation(JoinColumn.class);
		if (joinColumn != null && joinColumn.name().length() > 0) {
			return joinColumn.name();
		}

		if (joinColumns != null && joinColumns.length > 0 && joinColumns[0].name().length() > 0) {
			return joinColumns[0].name();
		}
		return defaultIdColumn;
	}

	/**
	 * Builds the name of the column that contains the ID of the entity for the given attribute.
	 *
	 * @param attribute
	 *            the inspected attribute
	 * @param override
	 *            contains optional override options
	 * @param joinTable
	 *            the optional join table date
	 * @param tableMetadata
	 *            the optional
	 * @param defaultIdColumn
	 *            the default name for the column, if {@code joinColumn} is empty or {@code null}
	 * @return the column name
	 */
	protected static String buildIdColumn(final AttributeAccessor attribute, final AssociationOverride override,
			final JoinTable joinTable, final CollectionTable tableMetadata, final String defaultIdColumn) {
		return buildIdColumn(attribute, override, joinTable != null ? joinTable.joinColumns()
				: tableMetadata != null ? tableMetadata.joinColumns() : null, defaultIdColumn);
	}

	/**
	 * Builds the name of the table of the association for the given field.
	 *
	 * @param attribute
	 *            the inspected field
	 * @param override
	 *            contains optional override options
	 * @param joinTable
	 *            the optional join table
	 * @param collectionTable
	 *            the optional metadata of the table
	 * @param defaultTableName
	 *            the default name for the table
	 * @return the table name
	 */
	protected static String buildTableName(final AttributeAccessor attribute, final AssociationOverride override,
			final JoinTable joinTable, final CollectionTable collectionTable, final String defaultTableName) {
		if (override != null) {
			final JoinTable joinTableOverride = override.joinTable();
			if (joinTableOverride != null && joinTableOverride.name().length() > 0) {
				return joinTableOverride.name();
			}
		}
		if (joinTable != null && joinTable.name().length() > 0) {
			return joinTable.name();
		}
		if (collectionTable != null && collectionTable.name().length() > 0) {
			return collectionTable.name();
		}
		return defaultTableName;
	}

	/**
	 * Builds the name of the table of the association for the given field.
	 *
	 * @param tableMetadata
	 *            the current metadata of the field
	 * @param defaultTableName
	 *            the default name for the table
	 * @return the column name
	 */
	protected static String buildTableName(final CollectionTable tableMetadata, final String defaultTableName) {
		return tableMetadata != null && tableMetadata.name().length() != 0 ? tableMetadata.name() : defaultTableName;
	}

	/**
	 * Builds the column that contains the value for the collection / map.
	 *
	 * @param table
	 *            the collection table
	 * @param attribute
	 *            the inspected attribute
	 * @param defaultColumnName
	 *            the default column name
	 * @return the column definition
	 */
	protected static GeneratorColumn buildValueColumn(final GeneratorTable table, final AttributeAccessor attribute,
			final String defaultColumnName) {
		final JoinTable tableMetadata = attribute.getAnnotation(JoinTable.class);
		if (tableMetadata != null && tableMetadata.inverseJoinColumns().length > 0
				&& tableMetadata.inverseJoinColumns()[0].name().length() > 0) {
			return table.resolveColumn(tableMetadata.inverseJoinColumns()[0].name());
		}
		final Column columnMetadata = attribute.getAnnotation(Column.class);
		if (columnMetadata != null && columnMetadata.name().length() > 0) {
			return table.resolveColumn(columnMetadata.name());
		}
		return table.resolveColumn(defaultColumnName);
	}

	private static String findMappedId(final AttributeAccessor attribute) {
		final MapsId mapsId = attribute.getAnnotation(MapsId.class);
		return mapsId == null || mapsId.value().length() == 0 ? null : mapsId.value();
	}

	/**
	 * Inspects the given attribute and searches for a generic type argument.
	 *
	 * @param attribute
	 *            the attribute to inspect
	 * @param explicitClass
	 *            an explicit class to use, if the metadata specified one
	 * @param argumentIndex
	 *            the index of the argument, for maps there are two: the key and the value
	 * @return the found class
	 */
	@SuppressWarnings("unchecked")
	protected static <T> Class<T> getPropertyArgument(final AttributeAccessor attribute, final Class<T> explicitClass,
			final int argumentIndex) {
		if (explicitClass != void.class) {
			// Explict target class
			return explicitClass;
		}

		// Inspect the type binding
		if (!(attribute.getGenericType() instanceof ParameterizedType)) {
			throw new ModelException(attribute + " is not of generic type and has no defined entity class");
		}

		final ParameterizedType type = (ParameterizedType) attribute.getGenericType();
		final Type[] parameterArgTypes = type.getActualTypeArguments();
		if (parameterArgTypes.length > argumentIndex) {
			return ClassUtil.getActualTypeBinding(attribute.getImplementationClass(),
					(Class<Object>) attribute.getDeclaringClass(), parameterArgTypes[argumentIndex]);
		}
		throw new ModelException(attribute + " has illegal generic type signature");

	}

	private static boolean useTargetTable(final AttributeAccessor attribute, final AssociationOverride override) {
		final JoinColumn joinColumn = override != null && override.joinColumns().length > 0 ? override.joinColumns()[0]
				: attribute.getAnnotation(JoinColumn.class);
		final JoinTable joinTable = override != null && override.joinTable() != null ? override.joinTable()
				: attribute.getAnnotation(JoinTable.class);
		return joinColumn != null && joinTable == null;

	}

	/** The current context. */
	private final GeneratorContext context;

	/** The current database dialect, as defined in the context. */
	private final GeneratorDialect dialect;

	/** Contains all properties of an embedded element collection. */
	private List<SingularProperty<T, ?>> embeddedProperties;

	/** Contains all properties of an embedded element collection which reference a required entity. */
	private List<EntityProperty<T, ?>> requiredEmbeddedProperties;

	/** The property to use, if an id is embedded. */
	private final String mappedId;

	/** The modified table. */
	private final GeneratorTable table;

	/** The column that contains the id of the entity. */
	private GeneratorColumn idColumn;

	/** Indicates that this property is defined by another property on the target type. */
	private final String mappedBy;

	/** Indicates to use a column of the target table. */
	private final boolean useTargetTable;

	/** The column that contains the value (or the id of the value). */
	private final GeneratorColumn valueColumn;

	/** The class of the value of the collection. */
	private final Class<T> valueClass;

	/** The description of the {@link #valueClass}, {@code null} if not an entity. */
	private final EntityClass<T> valueEntityClass;

	/** The converter for the value of the collection, {@code null} if not a primitive value. */
	private final ValueConverter<T> valueConverter;

	/**
	 * Creates a new property.
	 *
	 * @param sourceClass
	 *            the description of the current inspected class that contains this property
	 * @param attribute
	 *            accessor to the represented attribute
	 * @param override
	 *            the configured assocation override
	 * @param valueArgumentIndex
	 *            the index of the value argument in the collection class (0 for collection, 1 for map)
	 */
	// CHECKSTYLE OFF: NCS
	public PluralProperty(final EntityClass<?> sourceClass, final AttributeAccessor attribute,
			final AssociationOverride override, final int valueArgumentIndex) {
		super(attribute);
		this.context = sourceClass.getContext();
		this.dialect = this.context.getDialect();

		this.mappedId = findMappedId(attribute);

		// Check if we are OneToMany or ManyToMany or ElementCollection and initialize accordingly
		final CollectionTable collectionTable = attribute.getAnnotation(CollectionTable.class);
		final ElementCollection values = attribute.getAnnotation(ElementCollection.class);
		if (values != null) {
			// We are the owning side of the mapping
			this.mappedBy = null;
			this.useTargetTable = false;

			// Initialize the table and id column name
			this.table = this.context.resolveTable(
					buildTableName(collectionTable, sourceClass.getEntityName() + '_' + attribute.getName()));
			this.idColumn = this.table.resolveColumn(buildIdColumn(attribute, override, collectionTable,
					sourceClass.getEntityName() + '_' + sourceClass.getIdColumn(attribute)));

			// Initialize the target description and columns
			this.valueClass = getPropertyArgument(attribute, values.targetClass(), valueArgumentIndex);
			if (this.valueClass.isAnnotationPresent(Embeddable.class)) {
				buildEmbeddedProperties(this.valueClass);
				this.valueEntityClass = null;
				this.valueConverter = null;
				this.valueColumn = null;
			} else {
				this.valueEntityClass = sourceClass.getContext().getDescription(this.valueClass);
				// Check for primitive value
				this.valueConverter = this.valueEntityClass == null
						? PrimitiveProperty.createConverter(attribute, this.valueClass, false) : null;
				this.valueColumn = buildValueColumn(this.table, attribute, attribute.getName());
			}
		} else {
			// Entity mapping, either OneToMany or ManyToMany
			final OneToMany oneToMany = attribute.getAnnotation(OneToMany.class);
			if (oneToMany == null) {
				final ManyToMany manyToMany = attribute.getAnnotation(ManyToMany.class);
				Preconditions.checkArgument(manyToMany != null,
						attribute + " is neither declared as OneToMany nor ManyToMany nor ElementCollection");
				this.valueClass = getPropertyArgument(attribute, manyToMany.targetEntity(), valueArgumentIndex);
				this.mappedBy = manyToMany.mappedBy().length() == 0 ? null : manyToMany.mappedBy();
				this.useTargetTable = this.mappedBy != null;
			} else {
				this.valueClass = getPropertyArgument(attribute, oneToMany.targetEntity(), valueArgumentIndex);
				this.mappedBy = oneToMany.mappedBy().length() == 0 ? null : oneToMany.mappedBy();
				this.useTargetTable = this.mappedBy != null || useTargetTable(attribute, override);
			}

			// Resolve the target entity class
			this.valueEntityClass = sourceClass.getContext().getDescription(this.valueClass);

			// An entity mapping needs an entity class
			Preconditions.checkArgument(this.valueEntityClass != null, attribute + " has no entity as value");

			// No primitive value
			this.valueConverter = null;

			// Initialize the table and column names
			if (this.mappedBy != null) {
				// Bidirectional - use the columns of the target class
				this.table = this.valueEntityClass.getTable();
				// Find the mappedBy property later - may be that is not created in the target class up to now
				final Property<? super T, ?> idProperty = this.valueEntityClass.getIdProperty();
				Preconditions.checkArgument(idProperty instanceof SingularProperty,
						"Can only handle singular properties for ID in mapped by " + attribute);
				this.valueColumn = buildValueColumn(this.table, attribute,
						this.valueEntityClass.getIdColumn(attribute).getName());
			} else if (this.useTargetTable) {
				// Unidirectional and join column is in the table of the target class
				this.table = this.valueEntityClass.getTable();
				this.idColumn = this.table.resolveColumn(buildIdColumn(attribute, override, null, null,
						attribute.getName() + '_' + sourceClass.getIdColumn(attribute)));
				this.valueColumn = buildValueColumn(this.table, attribute,
						this.valueEntityClass.getIdColumn(attribute).getName());
			} else {
				// Unidirectional and we need a mapping table
				final JoinTable joinTable = attribute.getAnnotation(JoinTable.class);
				this.table = this.context.resolveTable(buildTableName(attribute, override, joinTable, collectionTable,
						sourceClass.getTable().getName() + '_' + this.valueEntityClass.getTable()));
				this.idColumn = this.table.resolveColumn(buildIdColumn(attribute, override, joinTable, collectionTable,
						sourceClass.getEntityName() + '_' + sourceClass.getIdColumn(attribute)));
				this.valueColumn = buildValueColumn(this.table, attribute,
						attribute.getName() + '_' + this.valueEntityClass.getIdColumn(attribute));
			}
		}

	}

	@Override
	public void addInsertExpression(final TableStatement statement, final E entity) {
		// Ignore
	}

	/**
	 * Builds the embedded properties of this property.
	 *
	 * @param targetType
	 *            the target type
	 */
	protected void buildEmbeddedProperties(final Class<?> targetType) {
		if (targetType.isAnnotationPresent(Embeddable.class)) {
			// Determine the access style
			final AccessStyle accessStyle;
			final Access accessType = targetType.getAnnotation(Access.class);
			if (accessType != null) {
				accessStyle = AccessStyle.getStyle(accessType.value());
			} else {
				accessStyle = getAttribute().getAccessStyle();
			}

			this.embeddedProperties = new ArrayList<>();
			this.requiredEmbeddedProperties = new ArrayList<>();
			final Map<String, AttributeOverride> attributeOverrides = EntityClass
					.getAttributeOverrides(getAttribute().getElement());
			final Map<String, AssociationOverride> accociationOverrides = EntityClass
					.getAccociationOverrides(getAttribute().getElement());
			for (final AttributeAccessor attribute : accessStyle.getDeclaredAttributes((Class<Object>) targetType,
					getAttribute().getImplementationClass())) {
				final AttributeOverride attributeOveride = attributeOverrides.get(attribute.getName());
				final Column columnMetadata = attributeOveride != null ? attributeOveride.column()
						: attribute.getAnnotation(Column.class);
				final AssociationOverride assocOverride = accociationOverrides.get(attribute.getName());
				final SingularProperty<T, ?> property = buildProperty(attribute, columnMetadata, assocOverride);
				if (property != null) {
					this.embeddedProperties.add(property);
					if (property.isRequired() && property instanceof EntityProperty) {
						this.requiredEmbeddedProperties.add((EntityProperty<T, ?>) property);
					}
				}
			}
		}
	}

	private SingularProperty<T, ?> buildProperty(final AttributeAccessor attribute, final Column columnMetadata,
			final AssociationOverride override) {
		// Ignore static, transient and generated fields
		if (attribute.isPersistent()) {
			if (CollectionProperty.isCollectionProperty(attribute) || MapProperty.isMapProperty(attribute)) {
				throw new ModelException("Plural attributes not allowed for embedded element collection: " + attribute);
			}
			if (EntityProperty.isEntityProperty(attribute)) {
				return new EntityProperty<>(this.context, this.table, attribute, override);
			}
			return new PrimitiveProperty<>(this.context, this.table, attribute, columnMetadata);
		}
		return null;
	}

	/**
	 * Creates the statements for a (not embeddable) value from the collection.
	 *
	 * @param writer
	 *            the target of created statements
	 * @param entity
	 *            the entity that contains the collection resp. map.
	 * @param sourceId
	 *            the ID of the current entity
	 * @param key
	 *            the value of the {@link #getKeyColumn()} - either the index or the map key
	 * @param value
	 *            the current value in the collection
	 * @throws IOException
	 *             if the writer throws one
	 */
	protected void createDirectValueStatement(final StatementsWriter writer, final E entity,
			final ColumnExpression sourceId, final ColumnExpression key, final T value) throws IOException {
		final ColumnExpression target = createValueExpression(entity, sourceId, value, key);
		if (target != null) {
			if (this.idColumn == null && this.mappedBy != null) {
				final Property<T, ?> mappedByProperty = this.valueEntityClass.getProperties().get(this.mappedBy);
				Preconditions.checkArgument(mappedByProperty != null,
						"Could not find property: " + this.mappedBy + " in " + this.valueClass);
				Preconditions.checkArgument(mappedByProperty instanceof SingularProperty,
						"Can only handle singular properties for mapped by in " + getAttribute().getElement());
				this.idColumn = ((SingularProperty<?, ?>) mappedByProperty).getColumn();
			}

			final TableStatement stmt;
			if (this.useTargetTable) {
				// Unidirectional, but from target table
				if (value == null) {
					return;
				}
				stmt = writer.createUpdateStatement(this.dialect, this.table, this.valueColumn, target);
				if (this.mappedBy == null) {
					stmt.setColumnValue(this.idColumn, sourceId);
				}
			} else {
				stmt = writer.createInsertStatement(this.dialect, this.table);
				stmt.setColumnValue(this.idColumn, sourceId);
				stmt.setColumnValue(this.valueColumn, target);
			}

			final GeneratorColumn keyColumn = getKeyColumn();
			if (keyColumn != null) {
				stmt.setColumnValue(keyColumn, key);
			}
			writer.writeStatement(stmt);
		}
	}

	/**
	 * Writes all embedded properties of a value of a collection table.
	 *
	 * @param writer
	 *            the target of the created statement
	 *
	 * @param entity
	 *            the entity that contains the collection resp. map.
	 * @param sourceId
	 *            the ID of the current entity
	 * @param key
	 *            the value of the {@link #getKeyColumn()} - either the index or the map key
	 * @param value
	 *            the current value in the collection
	 * @throws IOException
	 *             if the writer throws one
	 */
	private void createEmbeddedValueStatement(final StatementsWriter writer, final E entity,
			final ColumnExpression sourceId, final ColumnExpression key, final T value) throws IOException {
		for (final EntityProperty<T, ?> requiredProperty : this.requiredEmbeddedProperties) {
			final Object referencedEntity = requiredProperty.getValue(value);
			if (referencedEntity != null) {
				final EntityClass<Object> targetDescription = (EntityClass<Object>) this.context
						.getDescription(referencedEntity.getClass());
				final Property<Object, ?> idProperty = targetDescription.getIdProperty();
				if (idProperty.getValue(referencedEntity) == null) {
					// At least one of the required entities is not written up to now
					targetDescription.markPendingUpdates(referencedEntity, entity, this, key, value);
					return;
				}
			}
		}

		final TableStatement stmt = writer.createInsertStatement(this.dialect, getTable());
		stmt.setColumnValue(getIdColumn(), sourceId);
		if (getKeyColumn() != null) {
			stmt.setColumnValue(getKeyColumn(), key);
		}

		for (final SingularProperty<T, ?> property : this.embeddedProperties) {
			property.addInsertExpression(stmt, value);
		}
		writer.writeStatement(stmt);
	}

	/**
	 * Creates a SQL expression for a value of the collection, as long as it is not embedded.
	 *
	 * @param entity
	 *            the entity that contains the collection
	 * @param sourceId
	 *            the id of the entity as SQL expression
	 * @param key
	 *            the key/index of the entity in the collection as SQL expression
	 * @param value
	 *            the value that is written
	 * @return the expression or {@code null} if the insert is still pending
	 */
	protected ColumnExpression createValueExpression(final E entity, final ColumnExpression sourceId, final T value,
			final ColumnExpression key) {
		if (value == null) {
			return PrimitiveColumnExpression.NULL;
		}
		if (this.valueConverter != null) {
			return this.valueConverter.getExpression(value, getContext());
		}
		final ColumnExpression target = this.valueEntityClass.getEntityReference(value, this.mappedId,
				this.useTargetTable);
		if (target == null) {
			// Not created up to now
			this.valueEntityClass.markPendingUpdates(value, entity, this, key, value);
		}
		return target;
	}

	/**
	 * Adds a value statement to the list of collection statements.
	 *
	 * @param writer
	 *            the target of the created statements
	 * @param entity
	 *            the entity that contains the collection
	 * @param sourceId
	 *            the id of the entity as SQL expression
	 * @param key
	 *            the key/index of the entity in the collection as SQL expression
	 * @param value
	 *            the value that is written
	 * @throws IOException
	 *             if the writer throws one
	 */
	protected void createValueStatement(final StatementsWriter writer, final E entity, final ColumnExpression sourceId,
			final ColumnExpression key, final T value) throws IOException {
		if (isEmbedded()) {
			createEmbeddedValueStatement(writer, entity, sourceId, key, value);
		} else {
			createDirectValueStatement(writer, entity, sourceId, key, value);
		}
	}

	@Override
	public void generatePendingStatements(final StatementsWriter writer, final E entity, final Object writtenEntity,
			final Object... arguments) throws IOException {
		final ColumnExpression sourceId = EntityConverter.getEntityReference(entity, this.mappedId, getContext(),
				false);
		createValueStatement(writer, entity, sourceId, (ColumnExpression) arguments[0], (T) arguments[1]);
	}

	/**
	 * An optional column that contains the index / key of the values.
	 *
	 * @return the key column for map properties resp. the index column for list properties
	 */
	protected abstract GeneratorColumn getKeyColumn();

	/**
	 * Indicates that this propery is a {@link ElementCollection} that references {@link Embeddable}s.
	 *
	 * @return true if {@link #getEmbeddedProperties()} returns a list of properties
	 */
	public boolean isEmbedded() {
		return this.embeddedProperties != null;
	}

	/**
	 * Indicates that entities are referenced by the collection.
	 *
	 * @return {@code true} if the values of the collection are entities
	 */
	protected boolean isEntityReference() {
		return this.valueEntityClass != null;
	}

	@Override
	public boolean isRequired() {
		return false;
	}

	@Override
	public boolean isTableColumn() {
		return false;
	}

}