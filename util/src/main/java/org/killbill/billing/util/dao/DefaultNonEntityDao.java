/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.util.dao;

import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.commons.profiling.Profiling;
import org.killbill.commons.profiling.Profiling.WithProfilingCallback;
import org.killbill.commons.profiling.ProfilingFeature.ProfilingFeatureType;
import org.skife.jdbi.v2.IDBI;

import org.killbill.billing.ObjectType;
import org.killbill.billing.util.cache.CacheController;
import org.killbill.billing.util.cache.CacheLoaderArgument;

public class DefaultNonEntityDao implements NonEntityDao {

    private final NonEntitySqlDao nonEntitySqlDao;
    private final WithCaching<UUID, Long> withCachingObjectId;
    private final WithCaching<Long, UUID> withCachingRecordId;

    @Inject
    public DefaultNonEntityDao(final IDBI dbi) {
        this.nonEntitySqlDao = dbi.onDemand(NonEntitySqlDao.class);
        this.withCachingObjectId = new WithCaching<UUID, Long>();
        this.withCachingRecordId = new WithCaching<Long, UUID>();
    }


    public Long retrieveRecordIdFromObject(@Nullable final UUID objectId, final ObjectType objectType, @Nullable final CacheController<Object, Object> cache) {
        final TableName tableName = TableName.fromObjectType(objectType);
        return withCachingObjectId.withCaching(new OperationRetrieval<UUID, Long>() {
            @Override
            public Long doRetrieve(final UUID objectOrRecordId, final ObjectType objectType) {
                return nonEntitySqlDao.getRecordIdFromObject(objectId.toString(), tableName.getTableName());
            }
        }, objectId, objectType, tableName, cache);
    }

    public Long retrieveAccountRecordIdFromObject(@Nullable final UUID objectId, final ObjectType objectType, @Nullable final CacheController<Object, Object> cache) {
        final TableName tableName = TableName.fromObjectType(objectType);
        return withCachingObjectId.withCaching(new OperationRetrieval<UUID, Long>() {
            @Override
            public Long doRetrieve(final UUID objectId, final ObjectType objectType) {
                switch (tableName) {
                    case TENANT:
                    case TAG_DEFINITIONS:
                    case TAG_DEFINITION_HISTORY:
                        return null;

                    case ACCOUNT:
                        return nonEntitySqlDao.getAccountRecordIdFromAccount(objectId.toString());

                    default:
                        return nonEntitySqlDao.getAccountRecordIdFromObjectOtherThanAccount(objectId.toString(), tableName.getTableName());
                }
            }
        }, objectId, objectType, tableName, cache);
    }

    public Long retrieveTenantRecordIdFromObject(@Nullable final UUID objectId, final ObjectType objectType, @Nullable final CacheController<Object, Object> cache) {
        final TableName tableName = TableName.fromObjectType(objectType);
        return withCachingObjectId.withCaching(new OperationRetrieval<UUID, Long>() {
            @Override
            public Long doRetrieve(final UUID objectId, final ObjectType objectType) {
                switch (tableName) {
                    case TENANT:
                        // Explicit cast to Long to avoid NPE (unboxing to long)
                        return objectId == null ? (Long) 0L : nonEntitySqlDao.getTenantRecordIdFromTenant(objectId.toString());

                    default:
                        return nonEntitySqlDao.getTenantRecordIdFromObjectOtherThanTenant(objectId.toString(), tableName.getTableName());
                }

            }
        }, objectId, objectType, tableName, cache);
    }

    @Override
    public UUID retrieveIdFromObject(final Long recordId, final ObjectType objectType, @Nullable final CacheController<Object, Object> cache) {
        if (objectType == ObjectType.TENANT && recordId == InternalCallContextFactory.INTERNAL_TENANT_RECORD_ID) {
            return null;
        }
        final TableName tableName = TableName.fromObjectType(objectType);
        return withCachingRecordId.withCaching(new OperationRetrieval<Long, UUID>() {
            @Override
            public UUID doRetrieve(final Long objectOrRecordId, final ObjectType objectType) {
                return nonEntitySqlDao.getIdFromObject(recordId, tableName.getTableName());
            }
        }, recordId, objectType, tableName, cache);
    }


    @Override
    public Long retrieveLastHistoryRecordIdFromTransaction(@Nullable final Long targetRecordId, final TableName tableName, final NonEntitySqlDao transactional) {
        // There is no caching here because the value returned changes as we add more history records, and so we would need some cache invalidation
        return transactional.getLastHistoryRecordId(targetRecordId, tableName.getTableName());
    }

    @Override
    public Long retrieveHistoryTargetRecordId(@Nullable final Long recordId, final TableName tableName) {
        return nonEntitySqlDao.getHistoryTargetRecordId(recordId, tableName.getTableName());
    }


    private interface OperationRetrieval<TypeIn, TypeOut> {
        public TypeOut doRetrieve(final TypeIn objectOrRecordId, final ObjectType objectType);
    }

    // 'cache' will be null for the CacheLoader classes -- or if cache is not configured.
    private class WithCaching<TypeIn, TypeOut> {

        private TypeOut withCaching(final OperationRetrieval<TypeIn, TypeOut> op, @Nullable final TypeIn objectOrRecordId, final ObjectType objectType, final TableName tableName, @Nullable final CacheController<Object, Object> cache) {

            final Profiling<TypeOut> prof = new Profiling<TypeOut>();
            if (objectOrRecordId == null) {
                return null;
            }
            if (cache != null) {
                final String key = (cache.getCacheType().isKeyPrefixedWithTableName()) ?
                                   tableName + CacheControllerDispatcher.CACHE_KEY_SEPARATOR + objectOrRecordId.toString() :
                                   objectOrRecordId.toString();
                return (TypeOut) cache.get(key, new CacheLoaderArgument(objectType));
            }
            final TypeOut result;
            try {
                result = prof.executeWithProfiling(ProfilingFeatureType.DAO_DETAILS,  "NonEntityDao (type = " +  objectType + ") cache miss", new WithProfilingCallback<TypeOut>() {
                    @Override
                    public <ExceptionType extends Throwable> TypeOut execute() throws ExceptionType {
                        return op.doRetrieve(objectOrRecordId, objectType);
                    }
                });
                return result;
            } catch (Throwable throwable) {
                // This is only because WithProfilingCallback throws a Throwable...
                throw new RuntimeException(throwable);
            }
        }
    }
}
