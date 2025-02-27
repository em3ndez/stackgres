/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.stackgres.stream.jobs.target.migration.dialect.postgres;

import io.debezium.connector.jdbc.dialect.DatabaseDialect;
import io.debezium.connector.jdbc.type.AbstractType;
import io.debezium.connector.jdbc.type.Type;
import org.apache.kafka.connect.data.Schema;

/**
 * An implementation of {@link Type} for {@code OID} data types.
 *
 * @author Chris Cranford
 */
class OidType extends AbstractType {

    public static final OidType INSTANCE = new OidType();

    @Override
    public String[] getRegistrationKeys() {
        return new String[]{ "OID" };
    }

    @Override
    public String getTypeName(DatabaseDialect dialect, Schema schema, boolean key) {
      return "oid";
    }

}
