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

package org.killbill.billing.entitlement.dao;

import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.entitlement.EntitlementTestSuiteWithEmbeddedDB;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.junction.DefaultBlockingState;

public class TestBlockingDao extends EntitlementTestSuiteWithEmbeddedDB {

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        final Account account = accountApi.createAccount(getAccountData(7), callContext);

        // Override the context with the right account record id
        internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
    }

    @Test(groups = "slow", description = "Check BlockingStateDao with a single service")
    public void testDaoWithOneService() {
        final UUID uuid = UUID.randomUUID();
        final String overdueStateName = "WayPassedItMan";
        final String service = "TEST";

        final boolean blockChange = true;
        final boolean blockEntitlement = false;
        final boolean blockBilling = false;

        clock.setDay(new LocalDate(2012, 4, 1));

        final BlockingState state1 = new DefaultBlockingState(uuid, BlockingStateType.ACCOUNT, overdueStateName, service, blockChange, blockEntitlement, blockBilling, clock.getUTCNow());
        blockingStateDao.setBlockingState(state1, clock, internalCallContext);

        clock.addDays(1);

        final String overdueStateName2 = "NoReallyThisCantGoOn";
        final BlockingState state2 = new DefaultBlockingState(uuid, BlockingStateType.ACCOUNT, overdueStateName2, service, blockChange, blockEntitlement, blockBilling, clock.getUTCNow());
        blockingStateDao.setBlockingState(state2, clock, internalCallContext);

        Assert.assertEquals(blockingStateDao.getBlockingStateForService(uuid, BlockingStateType.ACCOUNT, service, internalCallContext).getStateName(), state2.getStateName());

        final List<BlockingState> states = blockingStateDao.getBlockingAllForAccountRecordId(internalCallContext);
        Assert.assertEquals(states.size(), 2);

        Assert.assertEquals(states.get(0).getStateName(), overdueStateName);
        Assert.assertEquals(states.get(1).getStateName(), overdueStateName2);
    }

    @Test(groups = "slow", description = "Check BlockingStateDao with multiple services")
    public void testDaoWithMultipleServices() throws Exception {
        final UUID uuid = UUID.randomUUID();
        final String overdueStateName = "WayPassedItMan";
        final String service1 = "TEST";

        final boolean blockChange = true;
        final boolean blockEntitlement = false;
        final boolean blockBilling = false;

        final BlockingState state1 = new DefaultBlockingState(uuid, BlockingStateType.ACCOUNT, overdueStateName, service1, blockChange, blockEntitlement, blockBilling, clock.getUTCNow());
        blockingStateDao.setBlockingState(state1, clock, internalCallContext);
        clock.setDeltaFromReality(1000 * 3600 * 24);

        final String service2 = "TEST2";

        final String overdueStateName2 = "NoReallyThisCantGoOn";
        final BlockingState state2 = new DefaultBlockingState(uuid, BlockingStateType.ACCOUNT, overdueStateName2, service2, blockChange, blockEntitlement, blockBilling, clock.getUTCNow());
        blockingStateDao.setBlockingState(state2, clock, internalCallContext);

        final List<BlockingState> history2 = blockingStateDao.getBlockingAllForAccountRecordId(internalCallContext);
        Assert.assertEquals(history2.size(), 2);
        Assert.assertEquals(history2.get(0).getStateName(), overdueStateName);
        Assert.assertEquals(history2.get(1).getStateName(), overdueStateName2);
    }
}
