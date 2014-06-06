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

package org.killbill.billing.subscription.glue;

import org.killbill.billing.GuicyKillbillTestNoDBModule;
import org.killbill.billing.mock.glue.MockNonEntityDaoModule;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.platform.test.glue.TestPlatformModuleNoDB;
import org.killbill.billing.subscription.api.timeline.RepairSubscriptionLifecycleDao;
import org.killbill.billing.subscription.engine.dao.MockSubscriptionDaoMemory;
import org.killbill.billing.subscription.engine.dao.RepairSubscriptionDao;
import org.killbill.billing.subscription.engine.dao.SubscriptionDao;
import org.killbill.notificationq.MockNotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueConfig;
import org.killbill.notificationq.api.NotificationQueueService;
import org.skife.config.ConfigurationObjectFactory;

import com.google.common.collect.ImmutableMap;
import com.google.inject.name.Names;

public class TestDefaultSubscriptionModuleNoDB extends TestDefaultSubscriptionModule {

    public TestDefaultSubscriptionModuleNoDB(final KillbillConfigSource configSource) {
        super(configSource);
    }

    @Override
    protected void installSubscriptionDao() {
        bind(SubscriptionDao.class).to(MockSubscriptionDaoMemory.class).asEagerSingleton();
        bind(SubscriptionDao.class).annotatedWith(Names.named(REPAIR_NAMED)).to(RepairSubscriptionDao.class);
        bind(RepairSubscriptionLifecycleDao.class).annotatedWith(Names.named(REPAIR_NAMED)).to(RepairSubscriptionDao.class);
        bind(RepairSubscriptionDao.class).asEagerSingleton();
    }

    private void installNotificationQueue() {
        bind(NotificationQueueService.class).to(MockNotificationQueueService.class).asEagerSingleton();
        configureNotificationQueueConfig();
    }

    protected void configureNotificationQueueConfig() {
        final NotificationQueueConfig config = new ConfigurationObjectFactory(skifeConfigSource).buildWithReplacements(NotificationQueueConfig.class,
                                                                                                                       ImmutableMap.<String, String>of("instanceName", "main"));
        bind(NotificationQueueConfig.class).toInstance(config);
    }

    @Override
    protected void configure() {

        install(new GuicyKillbillTestNoDBModule(configSource));

        super.configure();

        install(new TestPlatformModuleNoDB(configSource));
        installNotificationQueue();

        install(new MockNonEntityDaoModule(configSource));

    }
}
