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

package org.killbill.billing.util.security.shiro.realm;

import javax.inject.Inject;
import javax.inject.Named;
import javax.sql.DataSource;

import org.apache.shiro.realm.jdbc.JdbcRealm;
import org.killbill.billing.platform.glue.KillBillPlatformModuleBase;
import org.killbill.billing.util.security.shiro.KillbillCredentialsMatcher;

public class KillBillJdbcRealm extends JdbcRealm {

    private final DataSource dataSource;

    @Inject
    public KillBillJdbcRealm(@Named(KillBillPlatformModuleBase.SHIRO_DATA_SOURCE_ID_NAMED) final DataSource dataSource) {

        super();
        this.dataSource = dataSource;
        configureSecurity();
        configureDataSource();
        setPermissionsLookupEnabled(true);
    }

    private void configureSecurity() {
        setSaltStyle(SaltStyle.COLUMN);
        setCredentialsMatcher(KillbillCredentialsMatcher.getCredentialsMatcher());
    }

    private void configureDataSource() {
        setDataSource(dataSource);
    }
}
