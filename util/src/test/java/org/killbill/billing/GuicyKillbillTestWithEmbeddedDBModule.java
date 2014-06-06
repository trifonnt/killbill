/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing;

import java.io.IOException;

import javax.sql.DataSource;

import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.commons.embeddeddb.EmbeddedDB;
import org.skife.jdbi.v2.IDBI;
import org.testng.Assert;

public class GuicyKillbillTestWithEmbeddedDBModule extends GuicyKillbillTestModule {

    public GuicyKillbillTestWithEmbeddedDBModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    @Override
    protected void configure() {
        super.configure();

        final EmbeddedDB instance = DBTestingHelper.get();
        bind(EmbeddedDB.class).toInstance(instance);

        try {
            bind(DataSource.class).toInstance(DBTestingHelper.get().getDataSource());
            bind(IDBI.class).toInstance(DBTestingHelper.getDBI());
        } catch (final IOException e) {
            Assert.fail(e.toString());
        }
    }
}
