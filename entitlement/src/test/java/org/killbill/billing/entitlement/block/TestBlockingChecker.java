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

package org.killbill.billing.entitlement.block;

import java.util.UUID;

import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.entitlement.EntitlementTestSuiteNoDB;
import org.killbill.billing.entitlement.api.BlockingApiException;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.dao.MockBlockingStateDao;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;

public class TestBlockingChecker extends EntitlementTestSuiteNoDB {

    private Account account;
    private SubscriptionBaseBundle bundle;
    private SubscriptionBase subscription;

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        final UUID accountId = UUID.randomUUID();
        account = Mockito.mock(Account.class);
        Mockito.when(account.getId()).thenReturn(accountId);

        bundle = Mockito.mock(SubscriptionBaseBundle.class);
        Mockito.when(bundle.getAccountId()).thenReturn(accountId);
        final UUID bundleId = UUID.randomUUID();
        Mockito.when(bundle.getId()).thenReturn(bundleId);
        Mockito.when(bundle.getExternalKey()).thenReturn("key");

        subscription = Mockito.mock(SubscriptionBase.class);
        Mockito.when(subscription.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(subscription.getBundleId()).thenReturn(bundleId);

        try {
            Mockito.when(subscriptionInternalApi.getBundleFromId(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(bundle);
        } catch (SubscriptionBaseApiException e) {
            Assert.fail(e.toString());
        }

        // Cleanup mock daos
        ((MockBlockingStateDao) blockingStateDao).clear();
    }


    private void setStateBundle(final boolean bC, final boolean bE, final boolean bB) {
        final BlockingState bundleState = new DefaultBlockingState(bundle.getId(), BlockingStateType.SUBSCRIPTION_BUNDLE,"state", "test-service", bC, bE, bB, clock.getUTCNow());
        blockingStateDao.setBlockingState(bundleState, clock, internalCallContext);
    }

    private void setStateAccount(final boolean bC, final boolean bE, final boolean bB) {
        final BlockingState accountState = new DefaultBlockingState(account.getId(), BlockingStateType.ACCOUNT, "state", "test-service", bC, bE, bB, clock.getUTCNow());
        blockingStateDao.setBlockingState(accountState, clock, internalCallContext);
    }

    private void setStateSubscription(final boolean bC, final boolean bE, final boolean bB) {
        final BlockingState subscriptionState = new DefaultBlockingState(subscription.getId(), BlockingStateType.SUBSCRIPTION, "state", "test-service", bC, bE, bB, clock.getUTCNow());
        blockingStateDao.setBlockingState(subscriptionState, clock, internalCallContext);
    }

    @Test(groups = "fast")
    public void testSubscriptionChecker() throws Exception {
        setStateAccount(false, false, false);
        setStateBundle(false, false, false);
        setStateSubscription(false, false, false);
        blockingChecker.checkBlockedChange(subscription, internalCallContext);
        blockingChecker.checkBlockedEntitlement(subscription, internalCallContext);
        blockingChecker.checkBlockedBilling(subscription, internalCallContext);

        //BLOCKED SUBSCRIPTION
        clock.addDays(1);
        setStateSubscription(true, false, false);
        blockingChecker.checkBlockedEntitlement(subscription, internalCallContext);
        blockingChecker.checkBlockedBilling(subscription, internalCallContext);
        try {
            blockingChecker.checkBlockedChange(subscription, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        clock.addDays(1);
        setStateSubscription(false, true, false);
        blockingChecker.checkBlockedChange(subscription, internalCallContext);
        blockingChecker.checkBlockedBilling(subscription, internalCallContext);
        try {
            blockingChecker.checkBlockedEntitlement(subscription, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        clock.addDays(1);
        setStateSubscription(false, false, true);
        blockingChecker.checkBlockedChange(subscription, internalCallContext);
        blockingChecker.checkBlockedEntitlement(subscription, internalCallContext);
        try {
            blockingChecker.checkBlockedBilling(subscription, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        //BLOCKED BUNDLE
        clock.addDays(1);
        setStateSubscription(false, false, false);
        setStateBundle(true, false, false);
        blockingChecker.checkBlockedEntitlement(subscription, internalCallContext);
        blockingChecker.checkBlockedBilling(subscription, internalCallContext);
        try {
            blockingChecker.checkBlockedChange(subscription, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        clock.addDays(1);
        setStateBundle(false, true, false);
        blockingChecker.checkBlockedChange(subscription, internalCallContext);
        blockingChecker.checkBlockedBilling(subscription, internalCallContext);
        try {
            blockingChecker.checkBlockedEntitlement(subscription, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        clock.addDays(1);
        setStateBundle(false, false, true);
        blockingChecker.checkBlockedChange(subscription, internalCallContext);
        blockingChecker.checkBlockedEntitlement(subscription, internalCallContext);
        try {
            blockingChecker.checkBlockedBilling(subscription, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        //BLOCKED ACCOUNT
        clock.addDays(1);
        setStateSubscription(false, false, false);
        setStateBundle(false, false, false);
        setStateAccount(true, false, false);
        blockingChecker.checkBlockedEntitlement(subscription, internalCallContext);
        blockingChecker.checkBlockedBilling(subscription, internalCallContext);
        try {
            blockingChecker.checkBlockedChange(subscription, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        clock.addDays(1);
        setStateAccount(false, true, false);
        blockingChecker.checkBlockedChange(subscription, internalCallContext);
        blockingChecker.checkBlockedBilling(subscription, internalCallContext);
        try {
            blockingChecker.checkBlockedEntitlement(subscription, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        clock.addDays(1);
        setStateAccount(false, false, true);
        blockingChecker.checkBlockedChange(subscription, internalCallContext);
        blockingChecker.checkBlockedEntitlement(subscription, internalCallContext);
        try {
            blockingChecker.checkBlockedBilling(subscription, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }
    }

    @Test(groups = "fast")
    public void testBundleChecker() throws Exception {
        setStateAccount(false, false, false);
        setStateBundle(false, false, false);
        setStateSubscription(false, false, false);
        blockingChecker.checkBlockedChange(bundle, internalCallContext);
        blockingChecker.checkBlockedEntitlement(bundle, internalCallContext);
        blockingChecker.checkBlockedBilling(bundle, internalCallContext);

        //BLOCKED BUNDLE
        clock.addDays(1);
        setStateSubscription(false, false, false);
        setStateBundle(true, false, false);
        blockingChecker.checkBlockedEntitlement(bundle, internalCallContext);
        blockingChecker.checkBlockedBilling(bundle, internalCallContext);
        try {
            blockingChecker.checkBlockedChange(bundle, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        clock.addDays(1);
        setStateBundle(false, true, false);
        blockingChecker.checkBlockedChange(bundle, internalCallContext);
        blockingChecker.checkBlockedBilling(bundle, internalCallContext);
        try {
            blockingChecker.checkBlockedEntitlement(bundle, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        clock.addDays(1);
        setStateBundle(false, false, true);
        blockingChecker.checkBlockedChange(bundle, internalCallContext);
        blockingChecker.checkBlockedEntitlement(bundle, internalCallContext);
        try {
            blockingChecker.checkBlockedBilling(bundle, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        //BLOCKED ACCOUNT
        clock.addDays(1);
        setStateSubscription(false, false, false);
        setStateBundle(false, false, false);
        setStateAccount(true, false, false);
        blockingChecker.checkBlockedEntitlement(bundle, internalCallContext);
        blockingChecker.checkBlockedBilling(bundle, internalCallContext);
        try {
            blockingChecker.checkBlockedChange(bundle, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        clock.addDays(1);
        setStateAccount(false, true, false);
        blockingChecker.checkBlockedChange(bundle, internalCallContext);
        blockingChecker.checkBlockedBilling(bundle, internalCallContext);
        try {
            blockingChecker.checkBlockedEntitlement(bundle, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        clock.addDays(1);
        setStateAccount(false, false, true);
        blockingChecker.checkBlockedChange(bundle, internalCallContext);
        blockingChecker.checkBlockedEntitlement(bundle, internalCallContext);
        try {
            blockingChecker.checkBlockedBilling(bundle, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }
    }


    @Test(groups = "fast")
    public void testAccountChecker() throws Exception {
        setStateAccount(false, false, false);
        setStateBundle(false, false, false);
        setStateSubscription(false, false, false);
        blockingChecker.checkBlockedChange(account, internalCallContext);
        blockingChecker.checkBlockedEntitlement(account, internalCallContext);
        blockingChecker.checkBlockedBilling(account, internalCallContext);

        //BLOCKED ACCOUNT
        clock.addDays(1);
        setStateSubscription(false, false, false);
        setStateBundle(false, false, false);
        setStateAccount(true, false, false);
        blockingChecker.checkBlockedEntitlement(account, internalCallContext);
        blockingChecker.checkBlockedBilling(account, internalCallContext);
        try {
            blockingChecker.checkBlockedChange(account, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        clock.addDays(1);
        setStateAccount(false, true, false);
        blockingChecker.checkBlockedChange(account, internalCallContext);
        blockingChecker.checkBlockedBilling(account, internalCallContext);
        try {
            blockingChecker.checkBlockedEntitlement(account, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        clock.addDays(1);
        setStateAccount(false, false, true);
        blockingChecker.checkBlockedChange(account, internalCallContext);
        blockingChecker.checkBlockedEntitlement(account, internalCallContext);
        try {
            blockingChecker.checkBlockedBilling(account, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }
    }
}
