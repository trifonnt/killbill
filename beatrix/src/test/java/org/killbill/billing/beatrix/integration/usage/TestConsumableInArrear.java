/*
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.beatrix.integration.usage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.integration.TestIntegrationBase;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.usage.api.SubscriptionUsageRecord;
import org.killbill.billing.usage.api.UnitUsageRecord;
import org.killbill.billing.usage.api.UsageRecord;
import org.killbill.billing.util.callcontext.CallContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestConsumableInArrear extends TestIntegrationBase {

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
    }

    @Test(groups = "slow")
    public void testSimple() throws Exception {

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        //
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.INVOICE);
        // Check bundle after BP got created otherwise we get an error from auditApi.
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));

        //
        // ADD ADD_ON ON THE SAME DAY
        //
        final DefaultEntitlement aoSubscription = addAOEntitlementAndCheckForCompletion(bpSubscription.getBundleId(), "Bullets", ProductCategory.ADD_ON, BillingPeriod.NO_BILLING_PERIOD, NextEvent.CREATE);

        setUsage(aoSubscription.getId(), "bullets", new LocalDate(2012, 4, 1), 99L, callContext);
        setUsage(aoSubscription.getId(), "bullets", new LocalDate(2012, 4, 15), 100L, callContext);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2012, 5, 1));
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.USAGE, new BigDecimal("5.90")));

        // We don't expect any invoice, but we want to give the system the time to verify there is nothing to do so we can fail
        clock.setDay(new LocalDate(2012, 6, 1));
        Thread.sleep(1000);

        setUsage(aoSubscription.getId(), "bullets", new LocalDate(2012, 6, 1), 50L, callContext);
        setUsage(aoSubscription.getId(), "bullets", new LocalDate(2012, 6, 16), 300L, callContext);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2012, 7, 1));
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.USAGE, new BigDecimal("11.80")));


        // Should be ignored because this is outside of optimization range (org.killbill.invoice.readMaxRawUsagePreviousPeriod = 2) => we will only look for items > 2012-7-1 - 2 months = 2012-5-1
        setUsage(aoSubscription.getId(), "bullets", new LocalDate(2012, 4, 30), 100L, callContext);

        // Should be invoiced from past period
        setUsage(aoSubscription.getId(), "bullets", new LocalDate(2012, 5, 1), 199L, callContext);

        // New usage for this past period
        setUsage(aoSubscription.getId(), "bullets", new LocalDate(2012, 7, 1), 50L, callContext);
        setUsage(aoSubscription.getId(), "bullets", new LocalDate(2012, 7, 16), 300L, callContext);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2012, 8, 1));
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.USAGE, new BigDecimal("5.90")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 1), new LocalDate(2012, 8, 1), InvoiceItemType.USAGE, new BigDecimal("11.80")));

    }

    private void setUsage(final UUID subscriptionId, final String unitType, final LocalDate startDate, final Long amount, final CallContext context) {
        final List<UsageRecord> usageRecords = new ArrayList<UsageRecord>();
        usageRecords.add(new UsageRecord(startDate, amount));
        final List<UnitUsageRecord> unitUsageRecords = new ArrayList<UnitUsageRecord>();
        unitUsageRecords.add(new UnitUsageRecord(unitType, usageRecords));
        final SubscriptionUsageRecord record = new SubscriptionUsageRecord(subscriptionId, unitUsageRecords);
        usageUserApi.recordRolledUpUsage(record, context);
    }
}
