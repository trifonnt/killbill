/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.util.security.shiro.dao;

import java.util.List;

import javax.inject.Inject;

import org.apache.shiro.crypto.RandomNumberGenerator;
import org.apache.shiro.crypto.SecureRandomNumberGenerator;
import org.apache.shiro.crypto.hash.SimpleHash;
import org.apache.shiro.util.ByteSource;
import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.security.SecurityApiException;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.security.shiro.KillbillCredentialsMatcher;
import org.killbill.clock.Clock;
import org.killbill.commons.jdbi.mapper.LowerToCamelBeanMapperFactory;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;

public class DefaultUserDao implements UserDao {

    private static final RandomNumberGenerator rng = new SecureRandomNumberGenerator();
    private final IDBI dbi;
    private final Clock clock;

    @Inject
    public DefaultUserDao(final IDBI dbi, final Clock clock) {
        this.dbi = dbi;
        this.clock = clock;
        ((DBI) dbi).registerMapper(new LowerToCamelBeanMapperFactory(UserModelDao.class));
        ((DBI) dbi).registerMapper(new LowerToCamelBeanMapperFactory(UserRolesModelDao.class));
        ((DBI) dbi).registerMapper(new LowerToCamelBeanMapperFactory(RolesPermissionsModelDao.class));

    }

    @Override
    public void insertUser(final String username, final String password, final List<String> roles, final String createdBy) throws SecurityApiException {

        final ByteSource salt = rng.nextBytes();
        final String hashedPasswordBase64 = new SimpleHash(KillbillCredentialsMatcher.HASH_ALGORITHM_NAME,
                                                           password, salt.toBase64(), KillbillCredentialsMatcher.HASH_ITERATIONS).toBase64();

        final DateTime createdDate = clock.getUTCNow();
        dbi.inTransaction(new TransactionCallback<Void>() {
            @Override
            public Void inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final UserRolesSqlDao userRolesSqlDao = handle.attach(UserRolesSqlDao.class);
                for (String role : roles) {
                    final RolesPermissionsSqlDao rolesPermissionsSqlDao = handle.attach(RolesPermissionsSqlDao.class);
                    final List<RolesPermissionsModelDao> currentRolePermissions = rolesPermissionsSqlDao.getByRoleName(role);
                    if (currentRolePermissions.isEmpty()) {
                        throw new SecurityApiException(ErrorCode.SECURITY_INVALID_ROLE, role);
                    }
                    userRolesSqlDao.create(new UserRolesModelDao(username, role, createdDate, createdBy));
                }
                final UsersSqlDao usersSqlDao = handle.attach(UsersSqlDao.class);
                usersSqlDao.create(new UserModelDao(username, hashedPasswordBase64, salt.toBase64(), createdDate, createdBy));
                return null;
            }
        });
    }

    public List<UserRolesModelDao> getUserRoles(final String username) {
        return dbi.inTransaction(new TransactionCallback<List<UserRolesModelDao>>() {
            @Override
            public List<UserRolesModelDao> inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final UserRolesSqlDao userRolesSqlDao = handle.attach(UserRolesSqlDao.class);
                return userRolesSqlDao.getByUsername(username);
            }
        });

    }

    @Override
    public void addRoleDefinition(final String role, final List<String> permissions, final String createdBy) {
        final DateTime createdDate = clock.getUTCNow();
        dbi.inTransaction(new TransactionCallback<Void>() {
            @Override
            public Void inTransaction(final Handle handle, final TransactionStatus status) throws Exception {

                final RolesPermissionsSqlDao rolesPermissionsSqlDao = handle.attach(RolesPermissionsSqlDao.class);
                for (String permission : permissions) {
                    rolesPermissionsSqlDao.create(new RolesPermissionsModelDao(role, permission, createdDate, createdBy));
                }
                return null;
            }
        });

    }

    @Override
    public List<RolesPermissionsModelDao> getRoleDefinition(final String role) {
        return dbi.inTransaction(new TransactionCallback<List<RolesPermissionsModelDao>>() {
            @Override
            public List<RolesPermissionsModelDao> inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final RolesPermissionsSqlDao rolesPermissionsSqlDao = handle.attach(RolesPermissionsSqlDao.class);
                return rolesPermissionsSqlDao.getByRoleName(role);
            }
        });
    }

}
